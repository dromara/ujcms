package com.ofwise.util.lucene;

import com.ofwise.util.query.OffsetLimitRequest;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Lucene 操作模板
 *
 * @author PONY
 */
public class LuceneOperations {
    private boolean autoCommit;
    private IndexWriter indexWriter;
    private SearcherManager searcherManager;

    public LuceneOperations(IndexWriter indexWriter, SearcherManager searcherManager, boolean autoCommit) {
        this.indexWriter = indexWriter;
        this.searcherManager = searcherManager;
        this.autoCommit = autoCommit;
    }

    public LuceneOperations(IndexWriter indexWriter, SearcherManager searcherManager) {
        this(indexWriter, searcherManager, true);
    }

    public <T> List<T> list(Query query, OffsetLimitRequest offsetLimit, Sort sort, Function<Document, T> handel) {
        try {
            searcherManager.maybeRefresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                int n = (int) offsetLimit.getOffset() + offsetLimit.getPageSize();
                TopDocs results = searcher.search(query, n, sort);
                int length = results.scoreDocs.length;
                List<T> list = new ArrayList<>(length);
                for (ScoreDoc hit : results.scoreDocs) {
                    list.add(handel.apply(searcher.doc(hit.doc)));
                }
                return list;
            } finally {
                searcherManager.release(searcher);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during lucene searching.", e);
        }
    }

    public <T> Page<T> page(Query query, Pageable pageable, Sort sort, Function<Document, T> handel) {
        try {
            searcherManager.maybeRefresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                int n = (int) pageable.getOffset() + pageable.getPageSize();
                TopDocs results = searcher.search(query, n, sort);
                int length = results.scoreDocs.length;
                int size = length - (int) pageable.getOffset();
                List<T> content = Collections.emptyList();
                if (size > 0) {
                    content = new ArrayList<>(size);
                    for (int i = (int) pageable.getOffset(); i < length; i++) {
                        content.add(handel.apply(searcher.doc(results.scoreDocs[i].doc)));
                    }
                }
                long total = results.totalHits.value;
                return new PageImpl<>(content, pageable, total);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during lucene searching.", e);
        }
    }

    public void addDocument(Document document) {
        try {
            indexWriter.addDocument(document);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during lucene adding a document.", e);
        }
    }

    public void addDocuments(Collection<Document> documents) {
        try {
            indexWriter.addDocuments(documents);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during lucene adding a document.", e);
        }

    }

    public void updateDocument(Term term, Document document) {
        try {
            indexWriter.updateDocument(term, document);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during lucene updating a document.", e);
        }
    }

    public void deleteDocuments(Term... terms) {
        try {
            indexWriter.deleteDocuments(terms);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during lucene deleting a document.", e);
        }
    }

    public void deleteDocuments(Term term) {
        try {
            indexWriter.deleteDocuments(term);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during lucene deleting a document.", e);
        }
    }

    public void deleteDocuments(Query... queries) {
        try {
            indexWriter.deleteDocuments(queries);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during lucene deleting a document.", e);
        }
    }

    public void deleteDocuments(Query query) {
        try {
            indexWriter.deleteDocuments(query);
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during lucene deleting a document.", e);
        }
    }

    public void deleteAll() {
        try {
            indexWriter.deleteAll();
            if (autoCommit) {
                indexWriter.commit();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during lucene deleting a document.", e);
        }
    }

}
