package com.search;

import com.google.common.collect.ImmutableMap;
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
import org.elasticsearch.search.SearchHit;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

@Controller
public class ESController {

    private static final Logger logger = LoggerFactory.getLogger(ESController.class);

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
                long before = System.currentTimeMillis(), bulkBuilderLength = 0;
                BulkResponse bulkResponse;
                BulkRequestBuilder bulkRequest = client.prepareBulk();
                while (rs.next()) {
                    bulkRequest.add(client.prepareIndex("user", "user").setSource(
                            jsonBuilder().startObject()
                                    .field("id", String.valueOf(rs.getInt("id")))
                                    .field("name", rs.getString("name"))
                                    .field("level", rs.getString("level"))
                                    .field("gender", rs.getString("gender"))
                                    .field("address", rs.getString("address"))
                                    .endObject()));
                    bulkBuilderLength++;
                    if (0 == bulkBuilderLength % 10000) {
                        bulkResponse = bulkRequest.execute().actionGet();
                        if (bulkResponse.hasFailures()) {
                            logger.error("##### Bulk Request failure with error: " + bulkResponse.buildFailureMessage());
                        }
                        bulkRequest = client.prepareBulk();
                    }
                }
                if (bulkRequest.numberOfActions() > 0) {
                    bulkResponse = bulkRequest.execute().actionGet();
                    if (bulkResponse.hasFailures()) {
                        logger.error("##### Bulk Request failure with error: " + bulkResponse.buildFailureMessage());
                    }
                }
                logger.info("索引文档数: {}, 花费时长: {}ms", bulkBuilderLength, System.currentTimeMillis() - before);
            }
        } catch (Exception e) {
            logger.error("索引文档出现异常: {}", e.getMessage());
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
    public Object search() {
        Client client = null;
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            Settings settings = ImmutableSettings.settingsBuilder().put("cluster.name", "elasticsearch").build();
            client = new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
            long before = System.currentTimeMillis();
            SearchRequestBuilder builder = client.prepareSearch("user").setTypes("user")
                    .setSearchType(SearchType.DEFAULT).setFrom(0).setSize(10000);
            builder.setQuery(new QueryStringQueryBuilder("*:*"));
            SearchResponse response = builder.execute().actionGet();
            for (SearchHit hit : response.getHits()) {
                list.add(hit.getSource());
            }
            logger.info("总时长: {}ms,  TookInMillis: {}ms, TotalHits: {}", System.currentTimeMillis() - before, response.getTookInMillis(), response.getHits().getTotalHits());
        } catch (Exception e) {
            logger.error("搜索文档出现异常: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            if (null != client) {
                client.close();
            }
        }
        return ImmutableMap.builder().put("data", list).build();
    }
}