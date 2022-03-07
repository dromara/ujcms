package com.ofwise.util.freemarker;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import freemarker.core.Environment;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.PrintWriter;
import java.io.Writer;

/**
 * Freemarker 模板异常处理类
 * <p>
 * 根据 {@link TemplateExceptionHandler#HTML_DEBUG_HANDLER} 修改，去除繁琐的堆栈信息，有利用户快速找到错误原因。
 *
 * @author PONY
 */
public class SimpleTemplateExceptionHandler implements TemplateExceptionHandler {
    /**
     * 此段代码根据 Freemarker 官方代码编写
     *
     * @see TemplateExceptionHandler#HTML_DEBUG_HANDLER
     */
    @SuppressFBWarnings("OS_OPEN_STREAM")
    @Override
    public void handleTemplateException(TemplateException te, Environment env, Writer out) throws TemplateException {
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

            pw.println("[ERROR: " + te.getMessage() + "]");

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
