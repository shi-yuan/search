package com.search;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
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
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@Controller
public class SolrController {

    static final Logger logger = LoggerFactory.getLogger(SolrController.class);

    @Autowired
    private DataSource dataSource;

    /**
     * specified the core url
     */
    private static final String CORE_URL = "http://localhost:8888/solr/user";

    private static HttpSolrServer server;

    static {
        server = new HttpSolrServer(CORE_URL);
        // defaults to 0. > 1 not recommended.
        server.setMaxRetries(1);
        // establish TCP
        server.setConnectionTimeout(30000);
        // Setting the XML response parser is only required for cross
        // version compatibility and only when one side is 1.4.1 or
        // earlier and the other side is 3.1 or later.
        // binary parser is used by default
        server.setParser(new XMLResponseParser());
        // The following settings are provided here for completeness.
        // They will not normally be required, and should only be used
        // after consulting javadocs to know whether they are truly required.
        server.setSoTimeout(10000); // socket read timeout
        server.setDefaultMaxConnectionsPerHost(1000);
        server.setMaxTotalConnections(1000);
        server.setFollowRedirects(false);
        // defaults to false
        // allowCompression defaults to false.
        // Server side must support gzip or deflate for this to have any effect.
        server.setAllowCompression(true);
    }

    /**
     * 建立索引
     */
    @RequestMapping(value = "/index", method = RequestMethod.GET)
    @ResponseBody
    public String index() {
        indexDocsWithDB();
        return "success";
    }

    /**
     * 对数据库中的数据建立索引
     */
    private void indexDocsWithDB() {
        SqlRowSet rowSet = new JdbcTemplate(dataSource).queryForRowSet("SELECT t.id, t.name, t.level, t.gender, t.address FROM user t");
        List<SolrInputDocument> docs = new LinkedList<>();
        SolrInputDocument doc;
        if (null != rowSet) {
            while (rowSet.next()) {
                doc = new SolrInputDocument();
                doc.addField("id", rowSet.getInt("id"));
                doc.addField("name", rowSet.getString("name"));
                doc.addField("level", rowSet.getString("level"));
                doc.addField("gender", rowSet.getString("gender"));
                doc.addField("address", rowSet.getString("address"));
                logger.info("添加文档: {}", doc);
                docs.add(doc);
                if (docs.size() > 100) {
                    commitDocs(docs);
                }
            }
            if (docs.size() > 0) {
                commitDocs(docs);
            }
        }
    }

    private void commitDocs(List<SolrInputDocument> docs) {
        try {
            server.add(docs);
            server.commit();
            docs.clear();
        } catch (SolrServerException e) {
            logger.error("提交文档出现异常: {}", e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            logger.error("提交文档出现异常: {}", e.getMessage());
        }
    }

    /**
     * 搜索
     */
    @RequestMapping(value = "/search", method = RequestMethod.GET)
    @ResponseBody
    public String search() throws SolrServerException {
        SolrQuery query = new SolrQuery();
        query.setQuery("*:*");
        query.setShowDebugInfo(true);
        query.setStart(0);
        query.setRows(10);

        QueryResponse response = server.query(query);
        SolrDocumentList documents = response.getResults();
        if (null != documents) {
            logger.info("id   \t   name");
            logger.info("size: {}", documents.size());
            for (SolrDocument doc : documents) {
                logger.info(doc.getFieldValue("id") + ":" + "\t" + doc.getFieldValue("name"));
            }
        }
        return "success";
    }
}