package com.ujcms.cms.core;

import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.ArticleLuceneImpl;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.lucene.LuceneOperations;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.lionsoul.jcseg.ISegment;
import org.lionsoul.jcseg.analyzer.JcsegAnalyzer;
import org.lionsoul.jcseg.dic.ADictionary;
import org.lionsoul.jcseg.dic.DictionaryFactory;
import org.lionsoul.jcseg.segmenter.SegmenterConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Lucene 配置
 *
 * @author PONY
 */
@Configuration
public class LuceneConfig {
    private final ResourceLoader resourceLoader;
    private final Props props;

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

    public SegmenterConfig segmenterConfig() {
        return new SegmenterConfig(true);
    }

    public ADictionary dictionary() {
        return DictionaryFactory.createSingletonDictionary(segmenterConfig());
    }

    @Primary
    @Bean(destroyMethod = "close")
    public Analyzer analyzer() {
        return new JcsegAnalyzer(ISegment.COMPLEX, segmenterConfig(), dictionary());
    }

    @Bean(destroyMethod = "close")
    public Analyzer mostAnalyzer() {
        return new JcsegAnalyzer(ISegment.MOST, segmenterConfig(), dictionary());
    }

    /**
     * Lucene 索引写入配置
     */
    @Bean
    public IndexWriterConfig indexWriterConfig() {
        return new IndexWriterConfig(mostAnalyzer());
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
        // 全部使用最细分词。不可一个最细一个智能，因为最细分词不完全包含智能分词，导致某些内容无法搜索出结果。
        return new ArticleLuceneImpl(luceneOperations(), analyzer(), mostAnalyzer());
    }

}
