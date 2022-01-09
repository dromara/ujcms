package com.ujcms.core;

import com.ofwise.util.lucene.LuceneOperations;
import com.ujcms.core.lucene.ArticleLucene;
import com.ujcms.core.lucene.ArticleLuceneImpl;
import com.ujcms.core.support.Props;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Lucene 配置
 *
 * @author PONY
 */
@Configuration
public class LuceneConfig {
    private ResourceLoader resourceLoader;
    private Props props;

    public LuceneConfig(ResourceLoader resourceLoader, Props props) {
        this.resourceLoader = resourceLoader;
        this.props = props;
    }

    /**
     * Lucene 索引目录
     */
    @Bean(destroyMethod = "close")
    public Directory luceneDirectory() throws IOException {
        Path lucenePath = resourceLoader.getResource(props.getLucenePath()).getFile().toPath();
        return FSDirectory.open(lucenePath);
    }

    /**
     * Lucene IK 分词器
     */
    @Bean(destroyMethod = "close")
    public IKAnalyzer ikAnalyzer() {
        return new IKAnalyzer();
    }

    /**
     * Lucene 索引写入配置
     */
    @Bean
    public IndexWriterConfig indexWriterConfig() {
        return new IndexWriterConfig(ikAnalyzer());
    }

    /**
     * Lucene 索引写入器
     */
    @Bean(destroyMethod = "close")
    public IndexWriter indexWriter() throws IOException {
        return new IndexWriter(luceneDirectory(), indexWriterConfig());
    }

    /**
     * Lucene 搜索管理器
     */
    @Bean(destroyMethod = "close")
    public SearcherManager searcherManager() throws IOException {
        return new SearcherManager(indexWriter(), new SearcherFactory());
    }

    /**
     * Lucene 操作模板
     */
    @Bean
    public LuceneOperations luceneOperations() throws IOException {
        return new LuceneOperations(indexWriter(), searcherManager());
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.data.elasticsearch.repositories", name = "enabled", havingValue = "false")
    public ArticleLucene articleLucene() throws IOException {
        return new ArticleLuceneImpl(luceneOperations(), ikAnalyzer());
    }

}
