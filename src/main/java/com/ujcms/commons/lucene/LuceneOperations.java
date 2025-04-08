package com.ujcms.commons.lucene;

import com.ujcms.cms.core.lucene.domain.WebPage;
import com.ujcms.commons.query.OffsetLimitRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static com.ujcms.cms.core.lucene.domain.WebPage.FIELD_BODY;
import static com.ujcms.cms.core.lucene.domain.WebPage.FIELD_TITLE;

/**
 * Lucene 操作模板
 *
 * @author PONY
 */
public class LuceneOperations {
    private final boolean autoCommit;
    private final IndexWriter indexWriter;
    private final SearcherManager searcherManager;
    private final Analyzer analyzer;

    public LuceneOperations(IndexWriter indexWriter, SearcherManager searcherManager, Analyzer analyzer,
                            boolean autoCommit) {
        this.indexWriter = indexWriter;
        this.searcherManager = searcherManager;
        this.analyzer = analyzer;
        this.autoCommit = autoCommit;
    }

    public LuceneOperations(IndexWriter indexWriter, SearcherManager searcherManager, Analyzer analyzer) {
        this(indexWriter, searcherManager, analyzer, true);
    }

    public <T> List<T> list(Query query, OffsetLimitRequest offsetLimit, @Nullable Sort sort, Function<Document, T> handle) {
        try {
            searcherManager.maybeRefresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                TopDocs results = search(searcher, query, offsetLimit, sort);
                int length = results.scoreDocs.length;
                List<T> list = new ArrayList<>(length);
                for (ScoreDoc hit : results.scoreDocs) {
                    list.add(handle.apply(searcher.doc(hit.doc)));
                }
                return list;
            } finally {
                searcherManager.release(searcher);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error during lucene searching.", e);
        }
    }

    private static TopDocs search(IndexSearcher searcher, Query query, Pageable pageable, @Nullable Sort sort)
            throws IOException {
        int n = (int) pageable.getOffset() + pageable.getPageSize();
        if (sort != null) {
            return searcher.search(query, n, sort);
        }
        return searcher.search(query, n);
    }

    public <T extends WebPage> Page<T> page(Query query, Pageable pageable, @Nullable Sort sort, int fragmentSize,
                                            Function<Document, T> handle) {
        try {
            searcherManager.maybeRefresh();
            IndexSearcher searcher = searcherManager.acquire();
            IndexReader indexReader = searcher.getIndexReader();
            SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<em>", "</em>");
            Highlighter highlighter = new Highlighter(formatter, new QueryScorer(query));
            highlighter.setTextFragmenter(new SimpleFragmenter(fragmentSize));
            try {
                TopDocs results = search(searcher, query, pageable, sort);
                int length = results.scoreDocs.length;
                int size = length - (int) pageable.getOffset();
                List<T> content = new ArrayList<>(size);
                for (int i = (int) pageable.getOffset(); i < length; i++) {
                    int docId = results.scoreDocs[i].doc;

                    // 查看评分细节
                    // Explanation explanation = searcher.explain(query, results.scoreDocs[i].doc)
                    // System.out.println(explanation)

                    T bean = handle.apply(searcher.doc(docId));
                    // 处理高亮
                    Fields vectors = indexReader.getTermVectors(docId);
                    String title = bean.getTitle();
                    int maxStartOffset = highlighter.getMaxDocCharsToAnalyze() - 1;
                    TokenStream titleStream = TokenSources.getTokenStream(FIELD_TITLE, vectors, title,
                            analyzer, maxStartOffset);
                    bean.setHighlightTitle(highlighter.getBestFragment(titleStream, title));
                    // 这个方法更慢，弃用
                    // bean.setHighlightTitle(highlighter.getBestFragment(analyzer, FIELD_TITLE, title))
                    String body = bean.getBody();
                    if (StringUtils.isNotBlank(body)) {
                        TokenStream bodyStream = TokenSources.getTokenStream(FIELD_BODY, vectors, body,
                                analyzer, maxStartOffset);
                        bean.setHighlightBody(highlighter.getBestFragment(bodyStream, body));
                        // 这个方法更慢，弃用
                        // bean.setHighlightBody(highlighter.getBestFragment(analyzer, FIELD_BODY, body))
                    }
                    content.add(bean);
                }
                long total = results.totalHits.value;
                return new PageImpl<>(content, pageable, total);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error during lucene searching.", e);
        }
    }

    public void addDocument(Document document) {
        try {
            indexWriter.addDocument(document);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error during lucene adding a document.", e);
        }
    }

    public void addDocuments(Collection<Document> documents) {
        try {
            indexWriter.addDocuments(documents);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error during lucene adding a document.", e);
        }

    }

    public void updateDocument(Term term, Document document) {
        try {
            indexWriter.updateDocument(term, document);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Error during lucene updating a document.", e);
        }
    }

    public void deleteDocuments(Term... terms) {
        try {
            indexWriter.deleteDocuments(terms);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new IllegalStateException(DELETE_ERROR, e);
        }
    }

    public void deleteDocuments(Term term) {
        try {
            indexWriter.deleteDocuments(term);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new IllegalStateException(DELETE_ERROR, e);
        }
    }

    public void deleteDocuments(Query... queries) {
        try {
            indexWriter.deleteDocuments(queries);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new IllegalStateException(DELETE_ERROR, e);
        }
    }

    public void deleteDocuments(Query query) {
        try {
            indexWriter.deleteDocuments(query);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new IllegalStateException(DELETE_ERROR, e);
        }
    }

    public void deleteAll() {
        try {
            indexWriter.deleteAll();
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new IllegalStateException(DELETE_ERROR, e);
        }
    }

    private static final String DELETE_ERROR = "Error during lucene deleting a document.";
}
