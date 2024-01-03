package com.ujcms.commons.lucene;

import com.ujcms.commons.query.OffsetLimitRequest;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Lucene 操作模板
 *
 * @author PONY
 */
public class LuceneOperations {
    private final boolean autoCommit;
    private final IndexWriter indexWriter;
    private final SearcherManager searcherManager;

    public LuceneOperations(IndexWriter indexWriter, SearcherManager searcherManager, boolean autoCommit) {
        this.indexWriter = indexWriter;
        this.searcherManager = searcherManager;
        this.autoCommit = autoCommit;
    }

    public LuceneOperations(IndexWriter indexWriter, SearcherManager searcherManager) {
        this(indexWriter, searcherManager, true);
    }

    public <T> List<T> list(Query query, OffsetLimitRequest offsetLimit, Sort sort, Function<Document, T> handle) {
        try {
            searcherManager.maybeRefresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                int n = (int) offsetLimit.getOffset() + offsetLimit.getPageSize();
                TopFieldDocs results = searcher.search(query, n, sort);
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

    public <T> Page<T> page(Query query, Pageable pageable, Sort sort, Function<Document, T> handle) {
        try {
            searcherManager.maybeRefresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                int n = (int) pageable.getOffset() + pageable.getPageSize();
                TopFieldDocs results = searcher.search(query, n, sort);
                int length = results.scoreDocs.length;
                int size = length - (int) pageable.getOffset();
                List<T> content = new ArrayList<>(size);
                for (int i = (int) pageable.getOffset(); i < length; i++) {
                    content.add(handle.apply(searcher.doc(results.scoreDocs[i].doc)));
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
