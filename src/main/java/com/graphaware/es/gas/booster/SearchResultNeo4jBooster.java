/*
 * Copyright (c) 2013-2016 GraphAware
 *
 * This file is part of the GraphAware Framework.
 *
 * GraphAware Framework is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */
package com.graphaware.es.gas.booster;

import com.graphaware.es.gas.annotation.SearchBooster;
import com.graphaware.es.gas.domain.ExternalResult;
import com.graphaware.es.gas.domain.IndexInfo;
import com.graphaware.es.gas.util.UrlUtil;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;

import static com.graphaware.es.gas.domain.ClauseConstants.*;

@SearchBooster(name = "SearchResultNeo4jBooster")
public class SearchResultNeo4jBooster extends SearchResultExternalBooster {

    private static final String DEFAULT_KEY_PROPERTY = "uuid";
    private static final String DEFAULT_REST_ENDPOINT = "/graphaware/recommendation/filter";

    private String boosterEndpoint = null;
    private final ESLogger logger;
    private String targetId;
    private String keyProperty;

    public SearchResultNeo4jBooster(Settings settings, IndexInfo indexSettings) {
        super(settings, indexSettings);
        this.logger = Loggers.getLogger(IndexInfo.INDEX_LOGGER_NAME, settings);
    }

    @Override
    protected Map<String, ExternalResult> externalDoReorder(Set<String> keySet) {
        if (logger.isDebugEnabled()) {
            logger.debug("External Neo4j booster for : " + keySet);
            logger.debug("Call: " + getEndpoint());
        }
        return getReorderedResults(getExternalResults(keySet));

    }

    public Map<String, ExternalResult> getReorderedResults(List<ExternalResult> externalResults) {
        HashMap<String, ExternalResult> results = new HashMap<>();
        for (ExternalResult item : externalResults) {
            results.put(item.getObjectId(), item);
        }

        return results;
    }

    public List<ExternalResult> getExternalResults(Set<String> keySet) {

        ClientConfig cfg = new DefaultClientConfig();
        cfg.getClasses().add(JacksonJsonProvider.class);
        WebResource resource = Client.create(cfg).resource(getEndpoint());
        WebResource.Builder resBuilder = resource.accept(MediaType.APPLICATION_JSON);
        setHeader(resBuilder);
        ClientResponse response = null;
        List<ExternalResult> externalResults = null;
        
        try {
            response = resBuilder.post(ClientResponse.class, getParameters(keySet));
            GenericType<List<ExternalResult>> type = new GenericType<List<ExternalResult>>() {
            };
            externalResults = response.getEntity(type);
        }
        catch (UniformInterfaceException | ClientHandlerException ex) {
            throw new RuntimeException("Error while connecting to neo4j host", ex);
        }
        finally {
            if (response != null)
                response.close();
        }
        if (externalResults == null) {
            logger.error("Null results from neo4j endpoint");
            throw new RuntimeException("Null results from neo4j endpoint. No results returned");
        }
        return externalResults;
    }

    private void setHeader(WebResource.Builder resBuilder) {
      Map<String, String> headers = new HashMap<>();
      if (null != getNeo4jPassword()) {
        headers.put(HttpHeaders.AUTHORIZATION, UrlUtil.getAuthorizationHeaderValue(getNeo4jUsername(), getNeo4jPassword()));
      }
      for (String k : headers.keySet()) {
        resBuilder.header(k, headers.get(k));
      }
    }

    @Override
    protected void extendedParseRequest(Map<String, String> extParams) {
        targetId = extParams.get(TARGET);
        keyProperty = extParams.get(KEY_PROPERTY) != null ? extParams.get(KEY_PROPERTY) : DEFAULT_KEY_PROPERTY;
        boosterEndpoint = extParams.get(NEO4J_ENDPOINT);
    }

    public MultivaluedMap getParameters(Set<String> keySet) {
        MultivaluedMap param = new MultivaluedMapImpl();
        param.add("limit", String.valueOf(Integer.MAX_VALUE));
        param.add("from", String.valueOf(getFrom()));
        param.add("keyProperty", getKeyProperty());
        param.add("ids", implodeKeySet(keySet));
        return param;
    }

    protected String getTargetId() {
        return targetId;
    }

    public String getKeyProperty() {
        return keyProperty;
    }

    public String getEndpoint() {
        String boosterUrl = null != boosterEndpoint ? boosterEndpoint : DEFAULT_REST_ENDPOINT;

        return UrlUtil.buildUrlFromParts(getNeo4jHost(), boosterUrl, targetId);
    }

    public String implodeKeySet(Set<String> keySet) {
        boolean isFirst = true;
        String ids = "";
        for (String id : keySet) {
            if (!isFirst) {
                ids = ids.concat(",");
            }
            isFirst = false;
            ids = ids.concat(id);
        }

        return ids;
    }

}
