package com.groupbyinc.api;

import com.groupbyinc.api.config.ConnectionConfiguration;
import com.groupbyinc.api.model.RefinementsResult;
import com.groupbyinc.api.model.Results;
import com.groupbyinc.common.apache.commons.collections4.MapUtils;
import com.groupbyinc.common.apache.commons.io.Charsets;
import com.groupbyinc.common.apache.commons.io.IOUtils;
import com.groupbyinc.common.apache.commons.lang3.StringUtils;
import com.groupbyinc.common.apache.http.Header;
import com.groupbyinc.common.apache.http.HttpResponse;
import com.groupbyinc.common.apache.http.client.config.RequestConfig;
import com.groupbyinc.common.apache.http.client.methods.HttpPost;
import com.groupbyinc.common.apache.http.client.utils.URIBuilder;
import com.groupbyinc.common.apache.http.entity.StringEntity;
import com.groupbyinc.common.apache.http.impl.client.CloseableHttpClient;
import com.groupbyinc.common.apache.http.impl.client.HttpClientBuilder;
import com.groupbyinc.common.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import com.groupbyinc.common.jackson.Mappers;
import com.groupbyinc.common.util.ThreadUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * <code>
 * The Bridge is the class responsible for marshalling a query to and from the search service.
 * Because the bridge holds a connection pool that is expensive to create, it is highly recommended
 * that the bridge is held in the application memory scope and reused where appropriate.
 * <b>Do not create a new bridge object for each request as you will incur overhead that will
 * bring down your UI servers when under heavy load!</b>
 * </code>
 *
 * @author Will Warren
 */
public abstract class AbstractBridge {

  public static final int DEFAULT_RETRY_TIMEOUT = 80;
  public static final int DEFAULT_MAX_TRIES = 3;
  public static final String CLUSTER = "/cluster";
  protected static final String COLON = ":";
  protected static final String HTTP = "http://";
  protected static final String HTTPS = "https://";
  private static final Logger LOG = Logger.getLogger(AbstractBridge.class.getName());
  private static final String SEARCH = "/search";
  private static final String REFINEMENTS = "/refinements";
  private static final String REFINEMENT_SEARCH = "/refinement";
  private static final String BODY = "\nbody:\n";
  private static final String EXCEPTION_FROM_BRIDGE = "Exception from bridge: ";

  private final ConnectionConfiguration config;
  private final RequestConfig requestConfig;
  private final String bridgeUrl;
  private final String bridgeRefinementsUrl;
  private final String bridgeRefinementSearchUrl;
  private final String bridgeClusterUrl;
  protected String clientKey;
  private CloseableHttpClient httpClient;
  private long retryTimeout = DEFAULT_RETRY_TIMEOUT;
  private long maxTries = DEFAULT_MAX_TRIES;
  private List<Header> headers = new ArrayList<Header>();

  /**
   * <code>
   * Constructor to create a bridge object that connects to the search api.
   *
   * JSON Reference:
   * The key as found in your key management page in the command center
   *
   *     {"clientKey": "<client key>"}
   *
   * </code>
   *
   * @param clientKey
   *         The key as found in your key management page in the command
   *         center.
   * @param baseUrl
   *         The base url the bridge is serving on.
   */
  public AbstractBridge(String clientKey, String baseUrl) {
    this(clientKey, baseUrl, true, new ConnectionConfiguration());
  }

  /**
   * <code>
   * Constructor to create a bridge object that connects to the search api.
   *
   * JSON Reference:
   * The key as found in your key management page in the command center
   *
   *     {"clientKey": "<client key>"}
   *
   * </code>
   *
   * @param clientKey
   *         The key as found in your key management page in the command
   *         center.
   * @param baseUrl
   *         The base url the bridge is serving on.
   * @param config
   *         Configuration for the underlying HttpClient instance.
   */
  public AbstractBridge(String clientKey, String baseUrl, ConnectionConfiguration config) {
    this(clientKey, baseUrl, true, config);
  }

  /**
   * <code>
   * Constructor to create a bridge object that connects to the search api.
   *
   * JSON Reference:
   * The key as found in your key management page in the command center
   *
   *     {"clientKey": "<client key>"}
   *
   * </code>
   *
   * @param clientKey
   *         The key as found in your key management page in the command
   *         center.
   * @param baseUrl
   *         The base url the bridge is serving on.
   * @param compressResponse
   *         true to compress the response content, false to send uncompressed response.
   */
  public AbstractBridge(String clientKey, String baseUrl, boolean compressResponse) {
    this(clientKey, baseUrl, compressResponse, new ConnectionConfiguration());
  }

  /**
   * <code>
   * Constructor to create a bridge object that connects to the search api.
   *
   * JSON Reference:
   * The key as found in your key management page in the command center
   *
   *     {"clientKey": "<client key>"}
   *
   * </code>
   *
   * @param clientKey
   *         The key as found in your key management page in the command
   *         center.
   * @param baseUrl
   *         The base url the bridge is serving on.
   * @param compressResponse
   *         true to compress the response content, false to send uncompressed response.
   * @param config
   *         Configuration for the underlying HttpClient instance.
   */
  public AbstractBridge(String clientKey, String baseUrl, boolean compressResponse, ConnectionConfiguration config) {
    try {
      new URI(baseUrl);
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Invalid url: " + baseUrl);
    }

    this.config = config;
    requestConfig = RequestConfig.custom()
        .setConnectTimeout(config.getConnectTimeout())
        .setConnectionRequestTimeout(config.getConnectionRequestTimeout())
        .setSocketTimeout(config.getSocketTimeout())
        .build();

    this.clientKey = clientKey;
    createClient(compressResponse);

    bridgeUrl = baseUrl + SEARCH;
    bridgeRefinementsUrl = bridgeUrl + REFINEMENTS;
    bridgeRefinementSearchUrl = baseUrl + REFINEMENT_SEARCH;
    bridgeClusterUrl = baseUrl + CLUSTER;
  }

  private void createClient(boolean compressResponse) {
    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    cm.setMaxTotal(config.getMaxConnections());
    cm.setDefaultMaxPerRoute(config.getMaxConnectionsPerRoute());

    HttpClientBuilder b = HttpClientBuilder.create();
    if (!compressResponse) {
      b.disableContentCompression();
    }
    httpClient = b.setConnectionManager(cm)
        .setDefaultRequestConfig(requestConfig)
        .build();
  }

  /**
   * @return
   *
   * @internal
   */
  public String getClusterBridgeUrl() {
    return bridgeClusterUrl;
  }

  /**
   * <code>
   * Connects to the search service, parses the response into a model
   * </code>
   *
   * @param query
   *         A query representing the search.
   *
   * @return Results object from the search service
   *
   * @throws IOException
   */
  public Results search(Query query) throws IOException {
    InputStream data = fireRequest(getBridgeUrl(), query.getQueryUrlParams(), query.getBridgeJson(clientKey), query.isReturnBinary());
    return map(data, query.isReturnBinary());
  }

  protected InputStream fireRequest(String url, Map<String, String> urlParams, String body, boolean returnBinary) throws IOException {
    HttpResponse response = postToBridge(url, urlParams, body);
    InputStream data = response.getEntity()
        .getContent();
    if (response.getStatusLine()
            .getStatusCode() != 200) {
      String status = response.getStatusLine()
          .toString();
      byte[] bytes = IOUtils.toByteArray(data);
      IOUtils.closeQuietly(data);
      handleErrorStatus(status, bytes, returnBinary);
    }
    return data;
  }

  /**
   * @return
   *
   * @internal
   */
  public String getBridgeUrl() {
    return bridgeUrl;
  }

  protected Results map(InputStream data, boolean returnBinary) {
    return Mappers.readValue(data, Results.class, returnBinary);
  }

  private HttpResponse postToBridge(String url, Map<String, String> urlParams, String bridgeJson) throws IOException {
    StringEntity entity = new StringEntity(bridgeJson, Charset.forName("UTF-8"));
    entity.setContentType("application/json");

    HttpResponse response = null;
    boolean successful = false;
    int tries = 0;
    SocketException lastError = null;
    while (!successful && tries < 3) {
      try {
        HttpPost httpPost = new HttpPost(generateURI(url, urlParams, tries));
        for (Header header : headers) {
          httpPost.addHeader(header);
        }
        httpPost.setEntity(entity);
        response = httpClient.execute(httpPost);
        successful = true;
      } catch (URISyntaxException e) {
        LOG.severe("Invalid request, failing");
        break;
      } catch (SocketException e) {
        ThreadUtils.sleep(retryTimeout);
        LOG.warning("Connection failed, retrying");
        lastError = e;
        tries++;
      }
    }
    if (tries < maxTries) {
      return response;
    }
    throw new IOException("Tried to connect three times to: " + url, lastError);
  }

  void handleErrorStatus(String status, byte[] bytes, boolean returnBinary) throws IOException {
    StringBuilder msg = new StringBuilder();
    try {
      String errors = map(new ByteArrayInputStream(bytes), returnBinary).getErrors();
      if (StringUtils.isNotBlank(errors)) {
        msg.append(", ")
            .append(errors);
      }
    } catch (Exception e) {
      LOG.warning("unable to parse error from response.");
    } finally {
      if (StringUtils.isBlank(msg)) {
        msg.append(BODY)
            .append(StringUtils.toEncodedString(bytes, Charsets.UTF_8));
      }
    }
    throw new IOException(EXCEPTION_FROM_BRIDGE + status + msg.toString());
  }

  protected URI generateURI(String url, Map<String, String> params, int tries) throws URISyntaxException {
    URIBuilder u = new URIBuilder(url);
    if (MapUtils.isNotEmpty(params)) {
      for (Map.Entry<String, String> e : params.entrySet()) {
        u.addParameter(e.getKey(), e.getValue());
      }
    }
    u.addParameter("retry", Integer.toString(tries));
    return u.build();
  }

  /**
   * <code>
   * Connects to the refinements service, parses the response into a model
   * Retrieves at most 10,000 refinements for the navigation specified.
   * </code>
   *
   * @param query
   *         A query representing the search.
   * @param navigationName
   *         The name of the navigation to get more refinements for.
   *
   * @return RefinementsResult object from the refinements service
   *
   * @throws IOException
   */
  public RefinementsResult refinements(Query query, String navigationName) throws IOException {
    InputStream data = fireRequest(getBridgeRefinementsUrl(), query.getQueryUrlParams(), query.getBridgeRefinementsJson(clientKey, navigationName), query.isReturnBinary());
    return mapRefinements(data, query.isReturnBinary());
  }

  /**
   * @return
   *
   * @internal
   */
  public String getBridgeRefinementsUrl() {
    return bridgeRefinementsUrl;
  }

  protected RefinementsResult mapRefinements(InputStream data, boolean returnBinary) {
    return Mappers.readValue(data, RefinementsResult.class, returnBinary);
  }

  /**
   * <code>
   * Cleanup HTTP connection pool.
   * </code>
   */
  public void shutdown() {
    IOUtils.closeQuietly(httpClient);
  }

  /**
   * <code>
   * Sets the retry timeout for a failed request.
   * </code>
   *
   * @param retryTimeout the retry timeout
   */
  public void setRetryTimeout(long retryTimeout) {
    this.retryTimeout = retryTimeout;
  }

  /**
   * <code>
   * Sets the maximum number of times to try a request before returning an error.
   * </code>
   *
   * @param maxTries the maximum number of request attempts
   */
  public void setMaxTries(long maxTries) {
    this.maxTries = maxTries;
  }

  public List<Header> getHeaders() {
    return headers;
  }

  /**
   * <code>
   * Set a list of headers.  Use `getHeaders().add(new BasicHeader())`
   * </code>
   * @param headers
     */
  public void setHeaders(List<Header> headers) {
    this.headers = headers;
  }

}
