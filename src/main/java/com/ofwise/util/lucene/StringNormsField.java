package com.ofwise.util.lucene;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.search.NormsFieldExistsQuery;
import org.apache.lucene.util.BytesRef;

/**
 * 与 {@link StringField} 类似，但包含 norms，方便使用 {@link NormsFieldExistsQuery} 进行查询
 *
 * @author PONY
 */
public class StringNormsField extends Field {
    /**
     * Indexed, not tokenized, indexes DOCS_ONLY, not stored.
     */
    public static final FieldType TYPE_NOT_STORED = new FieldType();

    /**
     * Indexed, not tokenized, indexes DOCS_ONLY, stored
     */
    public static final FieldType TYPE_STORED = new FieldType();

    static {
        TYPE_NOT_STORED.setIndexOptions(IndexOptions.DOCS);
        TYPE_NOT_STORED.setTokenized(false);
        TYPE_NOT_STORED.freeze();

        TYPE_STORED.setIndexOptions(IndexOptions.DOCS);
        TYPE_STORED.setStored(true);
        TYPE_STORED.setTokenized(false);
        TYPE_STORED.freeze();
    }

    /** Creates a new textual StringField, indexing the provided String value
     *  as a single token.
     *
     *  @param name field name
     *  @param value String value
     *  @param stored Store.YES if the content should also be stored
     *  @throws IllegalArgumentException if the field name or value is null.
     */
    public StringNormsField(String name, String value, Store stored) {
        super(name, value, stored == Store.YES ? TYPE_STORED : TYPE_NOT_STORED);
    }

    /** Creates a new binary StringField, indexing the provided binary (BytesRef)
     *  value as a single token.
     *
     *  @param name field name
     *  @param value BytesRef value.  The provided value is not cloned so
     *         you must not change it until the document(s) holding it
     *         have been indexed.
     *  @param stored Store.YES if the content should also be stored
     *  @throws IllegalArgumentException if the field name or value is null.
     */
    public StringNormsField(String name, BytesRef value, Store stored) {
        super(name, value, stored == Store.YES ? TYPE_STORED : TYPE_NOT_STORED);
    }
}
