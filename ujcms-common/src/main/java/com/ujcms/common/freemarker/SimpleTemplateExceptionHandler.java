/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Modified for UJCMS by 南昌蓝智科技有限公司 (https://www.ujcms.com). Derived from Apache FreeMarker 2.3.34:
 * freemarker.template.TemplateExceptionHandler (HTML_DEBUG_HANDLER). Changes: prints the escaped message
 * without the stack trace.
 * First modified before 2026-04-19, the start of this repository's history; later changes are in the VCS log.
 */

package com.ujcms.common.freemarker;

import java.io.PrintWriter;
import java.io.Writer;

import com.ujcms.common.web.Strings;

import freemarker.core.Environment;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

/**
 * Freemarker 模板异常处理类
 * <p>
 * 根据 {@link TemplateExceptionHandler#HTML_DEBUG_HANDLER} 修改，去除繁琐的堆栈信息，有利用户快速找到错误原因。
 * 且忽略空值异常。
 *
 * @author PONY
 */
public class SimpleTemplateExceptionHandler implements TemplateExceptionHandler {
    /**
     * 此段代码根据 Freemarker 官方代码编写
     *
     * @see TemplateExceptionHandler#HTML_DEBUG_HANDLER
     */
    @Override
    public void handleTemplateException(TemplateException te, Environment env, Writer out) throws TemplateException {
        // Freemarker 文档中有提到通过忽略异常来处理空值问题，但过多的异常可能导致性能损失。
        // if (te instanceof InvalidReferenceException) {
        //     // 忽略空值异常
        //     return;
        // }
        if (!env.isInAttemptBlock()) {
            boolean externalPw = out instanceof PrintWriter;
            PrintWriter pw = externalPw ? (PrintWriter) out : new PrintWriter(out);
            pw.print("<!-- FREEMARKER ERROR MESSAGE STARTS HERE -->"
                    + "<!-- ]]> -->"
                    + "<script language=javascript>//\"></script>"
                    + "<script language=javascript>//'></script>"
                    + "<script language=javascript>//\"></script>"
                    + "<script language=javascript>//'></script>"
                    + "</title></xmp></script></noscript></style></object>"
                    + "</head></pre></table>"
                    + "</form></table></table></table></a></u></i></b>"
                    + "<div align='left' "
                    + "style='background-color:#FFFF7C; "
                    + "display:block; border-top:double; padding:4px; margin:0; "
                    + "font-family:Arial,sans-serif; ");
            pw.println("'>"
                    + "<pre style='display:block; background: none; border: 0; margin:0; padding: 0;"
                    + "font-family:monospace; "
                    + "color:#A80000; font-size:12px; font-style:normal; font-variant:normal; "
                    + "font-weight:normal; text-decoration:none; text-transform: none; "
                    + "white-space: pre-wrap; white-space: -moz-pre-wrap; white-space: -pre-wrap; "
                    + "white-space: -o-pre-wrap; word-wrap: break-word;'>");

            pw.println("[ERROR: " + Strings.htmlEscape(te.getMessage()) + "]");

            pw.println("</pre></div></html>");
            // To commit the HTTP response
            pw.flush();
        }  // if (!env.isInAttemptBlock())
        // 此处抛出异常，会在日志信息里显示。有可能导致日志信息过于庞大。
        if (env.getLogTemplateExceptions()) {
            throw te;
        }
    }
}
