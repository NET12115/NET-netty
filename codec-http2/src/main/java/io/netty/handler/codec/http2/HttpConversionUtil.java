/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;

import java.net.URI;
import java.util.Iterator;
import java.util.Map.Entry;

import static io.netty.handler.codec.http.HttpScheme.HTTP;
import static io.netty.handler.codec.http.HttpScheme.HTTPS;
import static io.netty.handler.codec.http.HttpUtil.isAsteriskForm;
import static io.netty.handler.codec.http.HttpUtil.isOriginForm;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.util.AsciiString.EMPTY_STRING;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.StringUtil.isNullOrEmpty;
import static io.netty.util.internal.StringUtil.length;

/**
 * Provides utility methods and constants for the HTTP/2 to HTTP conversion
 */
public final class HttpConversionUtil {
    /**
     * The set of headers that should not be directly copied when converting headers from HTTP to HTTP/2.
     */
    private static final CharSequenceMap<AsciiString> HTTP_TO_HTTP2_HEADER_BLACKLIST =
            new CharSequenceMap<AsciiString>();
    static {
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaderNames.CONNECTION, EMPTY_STRING);
        @SuppressWarnings("deprecation")
        AsciiString keepAlive = HttpHeaderNames.KEEP_ALIVE;
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(keepAlive, EMPTY_STRING);
        @SuppressWarnings("deprecation")
        AsciiString proxyConnection = HttpHeaderNames.PROXY_CONNECTION;
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(proxyConnection, EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaderNames.TRANSFER_ENCODING, EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaderNames.HOST, EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaderNames.UPGRADE, EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(ExtensionHeaderNames.STREAM_ID.text(), EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(ExtensionHeaderNames.SCHEME.text(), EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(ExtensionHeaderNames.PATH.text(), EMPTY_STRING);
    }

    /**
     * This will be the method used for {@link HttpRequest} objects generated out of the HTTP message flow defined in <a
     * href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-8.1.">HTTP/2 Spec Message Flow</a>
     */
    public static final HttpMethod OUT_OF_MESSAGE_SEQUENCE_METHOD = HttpMethod.OPTIONS;

    /**
     * This will be the path used for {@link HttpRequest} objects generated out of the HTTP message flow defined in <a
     * href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-8.1.">HTTP/2 Spec Message Flow</a>
     */
    public static final String OUT_OF_MESSAGE_SEQUENCE_PATH = "";

    /**
     * This will be the status code used for {@link HttpResponse} objects generated out of the HTTP message flow defined
     * in <a href="http://tools.ietf.org/html/draft-ietf-httpbis-http2-16#section-8.1.">HTTP/2 Spec Message Flow</a>
     */
    public static final HttpResponseStatus OUT_OF_MESSAGE_SEQUENCE_RETURN_CODE = HttpResponseStatus.OK;

    /**
     * <a href="https://tools.ietf.org/html/rfc7540#section-8.1.2.3">rfc7540, 8.1.2.3</a> states the path must not
     * be empty, and instead should be {@code /}.
     */
    private static final AsciiString EMPTY_REQUEST_PATH = new AsciiString("/");

    private HttpConversionUtil() {
    }

    /**
     * Provides the HTTP header extensions used to carry HTTP/2 information in HTTP objects
     */
    public enum ExtensionHeaderNames {
        /**
         * HTTP extension header which will identify the stream id from the HTTP/2 event(s) responsible for generating a
         * {@code HttpObject}
         * <p>
         * {@code "x-http2-stream-id"}
         */
        STREAM_ID("x-http2-stream-id"),
        /**
         * HTTP extension header which will identify the scheme pseudo header from the HTTP/2 event(s) responsible for
         * generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-scheme"}
         */
        SCHEME("x-http2-scheme"),
        /**
         * HTTP extension header which will identify the path pseudo header from the HTTP/2 event(s) responsible for
         * generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-path"}
         */
        PATH("x-http2-path"),
        /**
         * HTTP extension header which will identify the stream id used to create this stream in a HTTP/2 push promise
         * frame
         * <p>
         * {@code "x-http2-stream-promise-id"}
         */
        STREAM_PROMISE_ID("x-http2-stream-promise-id"),
        /**
         * HTTP extension header which will identify the stream id which this stream is dependent on. This stream will
         * be a child node of the stream id associated with this header value.
         * <p>
         * {@code "x-http2-stream-dependency-id"}
         */
        STREAM_DEPENDENCY_ID("x-http2-stream-dependency-id"),
        /**
         * HTTP extension header which will identify the weight (if non-default and the priority is not on the default
         * stream) of the associated HTTP/2 stream responsible responsible for generating a {@code HttpObject}
         * <p>
         * {@code "x-http2-stream-weight"}
         */
        STREAM_WEIGHT("x-http2-stream-weight");

        private final AsciiString text;

        ExtensionHeaderNames(String text) {
            this.text = new AsciiString(text);
        }

        public AsciiString text() {
            return text;
        }
    }

    /**
     * Apply HTTP/2 rules while translating status code to {@link HttpResponseStatus}
     *
     * @param status The status from an HTTP/2 frame
     * @return The HTTP/1.x status
     * @throws Http2Exception If there is a problem translating from HTTP/2 to HTTP/1.x
     */
    public static HttpResponseStatus parseStatus(CharSequence status) throws Http2Exception {
        HttpResponseStatus result;
        try {
            result = HttpResponseStatus.parseLine(status);
            if (result == HttpResponseStatus.SWITCHING_PROTOCOLS) {
                throw connectionError(PROTOCOL_ERROR, "Invalid HTTP/2 status code '%d'", result.code());
            }
        } catch (Http2Exception e) {
            throw e;
        } catch (Throwable t) {
            throw connectionError(PROTOCOL_ERROR, t,
                            "Unrecognized HTTP status code '%s' encountered in translation to HTTP/1.x", status);
        }
        return result;
    }

    /**
     * Create a new object to contain the response data
     *
     * @param streamId The stream associated with the response
     * @param http2Headers The initial set of HTTP/2 headers to create the response with
     * @param validateHttpHeaders <ul>
     *        <li>{@code true} to validate HTTP headers in the http-codec</li>
     *        <li>{@code false} not to validate HTTP headers in the http-codec</li>
     *        </ul>
     * @return A new response object which represents headers/data
     * @throws Http2Exception see {@link #addHttp2ToHttpHeaders(int, Http2Headers, FullHttpMessage, boolean)}
     */
    public static FullHttpResponse toHttpResponse(int streamId, Http2Headers http2Headers, boolean validateHttpHeaders)
                    throws Http2Exception {
        HttpResponseStatus status = parseStatus(http2Headers.status());
        // HTTP/2 does not define a way to carry the version or reason phrase that is included in an
        // HTTP/1.1 status line.
        FullHttpResponse msg = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, validateHttpHeaders);
        addHttp2ToHttpHeaders(streamId, http2Headers, msg, false);
        return msg;
    }

    /**
     * Create a new object to contain the request data
     *
     * @param streamId The stream associated with the request
     * @param http2Headers The initial set of HTTP/2 headers to create the request with
     * @param validateHttpHeaders <ul>
     *        <li>{@code true} to validate HTTP headers in the http-codec</li>
     *        <li>{@code false} not to validate HTTP headers in the http-codec</li>
     *        </ul>
     * @return A new request object which represents headers/data
     * @throws Http2Exception see {@link #addHttp2ToHttpHeaders(int, Http2Headers, FullHttpMessage, boolean)}
     */
    public static FullHttpRequest toHttpRequest(int streamId, Http2Headers http2Headers, boolean validateHttpHeaders)
                    throws Http2Exception {
        // HTTP/2 does not define a way to carry the version identifier that is included in the HTTP/1.1 request line.
        final CharSequence method = checkNotNull(http2Headers.method(),
                "method header cannot be null in conversion to HTTP/1.x");
        final CharSequence path = checkNotNull(http2Headers.path(),
                "path header cannot be null in conversion to HTTP/1.x");
        FullHttpRequest msg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method
                        .toString()), path.toString(), validateHttpHeaders);
        addHttp2ToHttpHeaders(streamId, http2Headers, msg, false);
        return msg;
    }

    /**
     * Translate and add HTTP/2 headers to HTTP/1.x headers.
     *
     * @param streamId The stream associated with {@code sourceHeaders}.
     * @param sourceHeaders The HTTP/2 headers to convert.
     * @param destinationMessage The object which will contain the resulting HTTP/1.x headers.
     * @param addToTrailer {@code true} to add to trailing headers. {@code false} to add to initial headers.
     * @throws Http2Exception If not all HTTP/2 headers can be translated to HTTP/1.x.
     * @see #addHttp2ToHttpHeaders(int, Http2Headers, HttpHeaders, HttpVersion, boolean, boolean)
     */
    public static void addHttp2ToHttpHeaders(int streamId, Http2Headers sourceHeaders,
                    FullHttpMessage destinationMessage, boolean addToTrailer) throws Http2Exception {
        addHttp2ToHttpHeaders(streamId, sourceHeaders,
                addToTrailer ? destinationMessage.trailingHeaders() : destinationMessage.headers(),
                destinationMessage.protocolVersion(), addToTrailer, destinationMessage instanceof HttpRequest);
    }

    /**
     * Translate and add HTTP/2 headers to HTTP/1.x headers.
     *
     * @param streamId The stream associated with {@code sourceHeaders}.
     * @param inputHeaders The HTTP/2 headers to convert.
     * @param outputHeaders The object which will contain the resulting HTTP/1.x headers..
     * @param httpVersion What HTTP/1.x version {@code outputHeaders} should be treated as when doing the conversion.
     * @param isTrailer {@code true} if {@code outputHeaders} should be treated as trailing headers.
     * {@code false} otherwise.
     * @param isRequest {@code true} if the {@code outputHeaders} will be used in a request message.
     * {@code false} for response message.
     * @throws Http2Exception If not all HTTP/2 headers can be translated to HTTP/1.x.
     */
    public static void addHttp2ToHttpHeaders(int streamId, Http2Headers inputHeaders, HttpHeaders outputHeaders,
            HttpVersion httpVersion, boolean isTrailer, boolean isRequest) throws Http2Exception {
        Http2ToHttpHeaderTranslator translator = new Http2ToHttpHeaderTranslator(streamId, outputHeaders, isRequest);
        try {
            for (Entry<CharSequence, CharSequence> entry : inputHeaders) {
                translator.translate(entry);
            }
        } catch (Http2Exception ex) {
            throw ex;
        } catch (Throwable t) {
            throw streamError(streamId, PROTOCOL_ERROR, t, "HTTP/2 to HTTP/1.x headers conversion error");
        }

        outputHeaders.remove(HttpHeaderNames.TRANSFER_ENCODING);
        outputHeaders.remove(HttpHeaderNames.TRAILER);
        if (!isTrailer) {
            outputHeaders.setInt(ExtensionHeaderNames.STREAM_ID.text(), streamId);
            HttpUtil.setKeepAlive(outputHeaders, httpVersion, true);
        }
    }

    /**
     * Converts the given HTTP/1.x headers into HTTP/2 headers.
     * The following headers are only used if they can not be found in from the {@code HOST} header or the
     * {@code Request-Line} as defined by <a href="https://tools.ietf.org/html/rfc7230">rfc7230</a>
     * <ul>
     * <li>{@link ExtensionHeaderNames#AUTHORITY}</li>
     * <li>{@link ExtensionHeaderNames#SCHEME}</li>
     * </ul>
     * {@link ExtensionHeaderNames#PATH} is ignored and instead extracted from the {@code Request-Line}.
     */
    public static Http2Headers toHttp2Headers(HttpMessage in, boolean validateHeaders) throws Exception {
        HttpHeaders inHeaders = in.headers();
        final Http2Headers out = new DefaultHttp2Headers(validateHeaders, inHeaders.size());
        if (in instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) in;
            URI requestTargetUri = URI.create(request.uri());
            out.path(toHttp2Path(requestTargetUri));
            out.method(request.method().asciiName());
            setHttp2Scheme(inHeaders, requestTargetUri, out);

            if (!isOriginForm(requestTargetUri) && !isAsteriskForm(requestTargetUri)) {
                // Attempt to take from HOST header before taking from the request-line
                String host = inHeaders.getAsString(HttpHeaderNames.HOST);
                setHttp2Authority((host == null || host.isEmpty()) ? requestTargetUri.getAuthority() : host, out);
            }
        } else if (in instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) in;
            out.status(new AsciiString(Integer.toString(response.status().code())));
        }

        // Add the HTTP headers which have not been consumed above
        toHttp2Headers(inHeaders, out);
        return out;
    }

    public static Http2Headers toHttp2Headers(HttpHeaders inHeaders, boolean validateHeaders) throws Exception {
        if (inHeaders.isEmpty()) {
            return EmptyHttp2Headers.INSTANCE;
        }

        final Http2Headers out = new DefaultHttp2Headers(validateHeaders, inHeaders.size());
        toHttp2Headers(inHeaders, out);
        return out;
    }

    public static void toHttp2Headers(HttpHeaders inHeaders, Http2Headers out) throws Exception {
        Iterator<Entry<CharSequence, CharSequence>> iter = inHeaders.iteratorCharSequence();
        while (iter.hasNext()) {
            Entry<CharSequence, CharSequence> entry = iter.next();
            final AsciiString aName = AsciiString.of(entry.getKey()).toLowerCase();
            if (!HTTP_TO_HTTP2_HEADER_BLACKLIST.contains(aName)) {
                // https://tools.ietf.org/html/rfc7540#section-8.1.2.2 makes a special exception for TE
                if (!aName.contentEqualsIgnoreCase(HttpHeaderNames.TE) ||
                    AsciiString.contentEqualsIgnoreCase(entry.getValue(), HttpHeaderValues.TRAILERS)) {
                    out.add(aName, AsciiString.of(entry.getValue()));
                }
            }
        }
    }

    /**
     * Generate a HTTP/2 {code :path} from a URI in accordance with
     * <a href="https://tools.ietf.org/html/rfc7230#section-5.3">rfc7230, 5.3</a>.
     */
    private static AsciiString toHttp2Path(URI uri) {
        StringBuilder pathBuilder = new StringBuilder(length(uri.getRawPath()) +
                length(uri.getRawQuery()) + length(uri.getRawFragment()) + 2);
        if (!isNullOrEmpty(uri.getRawPath())) {
            pathBuilder.append(uri.getRawPath());
        }
        if (!isNullOrEmpty(uri.getRawQuery())) {
            pathBuilder.append('?');
            pathBuilder.append(uri.getRawQuery());
        }
        if (!isNullOrEmpty(uri.getRawFragment())) {
            pathBuilder.append('#');
            pathBuilder.append(uri.getRawFragment());
        }
        String path = pathBuilder.toString();
        return path.isEmpty() ? EMPTY_REQUEST_PATH : new AsciiString(path);
    }

    private static void setHttp2Authority(String autority, Http2Headers out) {
        // The authority MUST NOT include the deprecated "userinfo" subcomponent
        if (autority != null) {
            int endOfUserInfo = autority.indexOf('@');
            if (endOfUserInfo < 0) {
                out.authority(new AsciiString(autority));
            } else if (endOfUserInfo + 1 < autority.length()) {
                out.authority(new AsciiString(autority.substring(endOfUserInfo + 1)));
            } else {
                throw new IllegalArgumentException("autority: " + autority);
            }
        }
    }

    private static void setHttp2Scheme(HttpHeaders in, URI uri, Http2Headers out) {
        String value = uri.getScheme();
        if (value != null) {
            out.scheme(new AsciiString(value));
            return;
        }

        // Consume the Scheme extension header if present
        CharSequence cValue = in.get(ExtensionHeaderNames.SCHEME.text());
        if (cValue != null) {
            out.scheme(AsciiString.of(cValue));
            return;
        }

        if (uri.getPort() == HTTPS.port()) {
            out.scheme(HTTPS.name());
        } else if (uri.getPort() == HTTP.port()) {
            out.scheme(HTTP.name());
        } else {
            throw new IllegalArgumentException(":scheme must be specified. " +
                    "see https://tools.ietf.org/html/rfc7540#section-8.1.2.3");
        }
    }

    /**
     * Utility which translates HTTP/2 headers to HTTP/1 headers.
     */
    private static final class Http2ToHttpHeaderTranslator {
        /**
         * Translations from HTTP/2 header name to the HTTP/1.x equivalent.
         */
        private static final CharSequenceMap<AsciiString>
            REQUEST_HEADER_TRANSLATIONS = new CharSequenceMap<AsciiString>();
        private static final CharSequenceMap<AsciiString>
            RESPONSE_HEADER_TRANSLATIONS = new CharSequenceMap<AsciiString>();
        static {
            RESPONSE_HEADER_TRANSLATIONS.add(Http2Headers.PseudoHeaderName.AUTHORITY.value(),
                            HttpHeaderNames.HOST);
            RESPONSE_HEADER_TRANSLATIONS.add(Http2Headers.PseudoHeaderName.SCHEME.value(),
                            ExtensionHeaderNames.SCHEME.text());
            REQUEST_HEADER_TRANSLATIONS.add(RESPONSE_HEADER_TRANSLATIONS);
            RESPONSE_HEADER_TRANSLATIONS.add(Http2Headers.PseudoHeaderName.PATH.value(),
                            ExtensionHeaderNames.PATH.text());
        }

        private final int streamId;
        private final HttpHeaders output;
        private final CharSequenceMap<AsciiString> translations;

        /**
         * Create a new instance
         *
         * @param output The HTTP/1.x headers object to store the results of the translation
         * @param request if {@code true}, translates headers using the request translation map. Otherwise uses the
         *        response translation map.
         */
        Http2ToHttpHeaderTranslator(int streamId, HttpHeaders output, boolean request) {
            this.streamId = streamId;
            this.output = output;
            translations = request ? REQUEST_HEADER_TRANSLATIONS : RESPONSE_HEADER_TRANSLATIONS;
        }

        public void translate(Entry<CharSequence, CharSequence> entry) throws Http2Exception {
            final CharSequence name = entry.getKey();
            final CharSequence value = entry.getValue();
            AsciiString translatedName = translations.get(name);
            if (translatedName != null) {
                output.add(translatedName, AsciiString.of(value));
            } else if (!Http2Headers.PseudoHeaderName.isPseudoHeader(name)) {
                // https://tools.ietf.org/html/rfc7540#section-8.1.2.3
                // All headers that start with ':' are only valid in HTTP/2 context
                if (name.length() == 0 || name.charAt(0) == ':') {
                    throw streamError(streamId, PROTOCOL_ERROR,
                            "Invalid HTTP/2 header '%s' encountered in translation to HTTP/1.x", name);
                }
                output.add(AsciiString.of(name), AsciiString.of(value));
            }
        }
    }
}
