/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for UJCMS by 南昌蓝智科技有限公司 (https://www.ujcms.com). Derived from Apache Lucene 8.11.4:
 * org.apache.lucene.document.StringField. Changes: norms are kept.
 * First modified before 2026-04-19, the start of this repository's history; later changes are in the VCS log.
 */

package com.ujcms.common.lucene;

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
