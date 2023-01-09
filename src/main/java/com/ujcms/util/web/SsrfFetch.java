package com.ujcms.util.web;

import com.ujcms.cms.core.web.backendapi.UploadController;
import org.apache.commons.validator.routines.DomainValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;

import static com.ujcms.util.web.Uploads.IMAGE_TYPE;

/**
 * @author PONY
 */
public class SsrfFetch {
    private static final Logger logger = LoggerFactory.getLogger(SsrfFetch.class);

    private SsrfFetch() {
    }
}
