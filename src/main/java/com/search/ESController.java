package com.search;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Controller
public class ESController {

    static final Logger logger = LoggerFactory.getLogger(ESController.class);

    @Autowired
    private DataSource dataSource;

    /**
     * 建立索引
     */
    @RequestMapping(value = "/es/index", method = RequestMethod.GET)
    @ResponseBody
    public String index() {
        Client client = null;
        try {
            SqlRowSet rs = new JdbcTemplate(dataSource).queryForRowSet("SELECT t.id, t.name, t.level, t.gender, t.address FROM user t");
            if (null != rs) {
                Settings settings = ImmutableSettings.settingsBuilder().put("cluster.name", "elasticsearch").build();
                client = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
                BulkRequestBuilder bulkRequest = client.prepareBulk();
                BulkResponse bulkResponse;
                long bulkBuilderLength = 0;
                while (rs.next()) {
                    bulkRequest.add(client.prepareIndex("user", "user").setSource(
                            jsonBuilder().startObject()
                                    .field("id", rs.getInt("id"))
                                    .field("name", rs.getString("name"))
                                    .field("level", rs.getString("level"))
                                    .field("gender", rs.getString("gender"))
                                    .field("address", rs.getString("address"))
                                    .endObject()));
                    bulkBuilderLength++;
                    if (0 == bulkBuilderLength % 10000) {
                        logger.info("##### " + bulkBuilderLength + " data indexed.");
                        bulkResponse = bulkRequest.execute().actionGet();
                        if (bulkResponse.hasFailures()) {
                            logger.error("##### Bulk Request failure with error: " + bulkResponse.buildFailureMessage());
                        }
                        bulkRequest = client.prepareBulk();
                    }
                }
                if (bulkRequest.numberOfActions() > 0) {
                    logger.info("##### " + bulkBuilderLength + " data indexed.");
                    bulkResponse = bulkRequest.execute().actionGet();
                    if (bulkResponse.hasFailures()) {
                        logger.error("##### Bulk Request failure with error: " + bulkResponse.buildFailureMessage());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != client) {
                client.close();
            }
        }
        return "success";
    }

    /**
     * 搜索
     */
    @RequestMapping(value = "/es/search", method = RequestMethod.GET)
    @ResponseBody
    public String search() {
        Client client = null;
        try {
            Settings settings = ImmutableSettings.settingsBuilder().put("cluster.name", "elasticsearch").build();
            client = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
            SearchRequestBuilder builder = client.prepareSearch("user").setTypes("user")
                    .setSearchType(SearchType.DEFAULT).setFrom(0).setSize(100);
            builder.setQuery(new QueryStringQueryBuilder("*:*"));
            SearchResponse response = builder.execute().actionGet();
            logger.info("took {} milliseconds", response.getTookInMillis());
            logger.info("totalHits: {}", response.getHits().getTotalHits());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != client) {
                client.close();
            }
        }
        return "success";
    }
}