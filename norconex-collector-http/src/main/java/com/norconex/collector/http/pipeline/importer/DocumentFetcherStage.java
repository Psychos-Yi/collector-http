/* Copyright 2015-2020 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.http.pipeline.importer;

import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.fetch.util.RedirectStrategyWrapper;

/**
 * <p>Fetches (i.e. download for processing) a document.</p>
 * <p>Prior to 2.3.0, the code for this class was part of
 * {@link HttpImporterPipeline}.
 * @author Pascal Essiembre
 * @since 2.3.0
 */
/*default*/ class DocumentFetcherStage extends AbstractImporterStage {

    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        HttpDocInfo crawlRef = ctx.getDocInfo();

        IHttpFetchResponse response = ctx.getHttpFetchClient().fetch(
                ctx.getDocument(), HttpMethod.GET);

        crawlRef.setCrawlDate(LocalDateTime.now());

        HttpImporterPipelineUtil.enhanceHTTPHeaders(
                ctx.getDocument().getMetadata());
        HttpImporterPipelineUtil.applyMetadataToDocument(ctx.getDocument());

        crawlRef.setContentType(
                ctx.getDocument().getDocInfo().getContentType());

        //-- Deal with redirects ---
        String redirectURL = RedirectStrategyWrapper.getRedirectURL();
        if (StringUtils.isNotBlank(redirectURL)) {
            HttpImporterPipelineUtil.queueRedirectURL(
                    ctx, response, redirectURL);
            return false;
        }

        CrawlState state = response.getCrawlState();
        crawlRef.setState(state);
        if (state.isGoodState()) {
            ctx.fireCrawlerEvent(CrawlerEvent.DOCUMENT_FETCHED,
                    crawlRef, response);
        } else {
            String eventType = null;
            if (state.isOneOf(CrawlState.NOT_FOUND)) {
                eventType = CrawlerEvent.REJECTED_NOTFOUND;
            } else {
                eventType = CrawlerEvent.REJECTED_BAD_STATUS;
            }
            ctx.fireCrawlerEvent(eventType, crawlRef, response);
            return false;
        }
        return true;
    }
}