package com.qihoo.epp.nac.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.util.StringUtil;
import com.qihoo.epp.nac.constant.OTPConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.io.FileUtils;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author wl
 */
@Slf4j
public class HttpClientUtil {

    // 编码格式。发送编码格式统一用UTF-8
    private static final String ENCODING = "UTF-8";

    // 设置连接超时时间，单位毫秒。
    private static int CONNECT_TIMEOUT = 100000;

    // 请求获取数据的超时时间(即响应时间)，单位毫秒。
    private static int SOCKET_TIMEOUT = 100000;


    public static void setConnectTimeout(int connectTimeout) {
        CONNECT_TIMEOUT = connectTimeout;
    }

    public static void setSocketTimeout(int socketTimeout) {
        SOCKET_TIMEOUT = socketTimeout;
    }


    private static HttpClientUtil httpClientUtil;

    public static CloseableHttpClient getHttpClient() {
        RegistryBuilder<ConnectionSocketFactory> registryBuilder = RegistryBuilder.<ConnectionSocketFactory>create();
        ConnectionSocketFactory plainSF = new PlainConnectionSocketFactory();
        registryBuilder.register("http", plainSF);
        //指定信任密钥存储对象和连接套接字工厂  
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            //信任任何链接  
            TrustStrategy anyTrustStrategy = new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    return true;
                }
            };
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(trustStore, anyTrustStrategy).build();
            HostnameVerifier hv = new HostnameVerifier() {
                @Override
                public boolean verify(String urlHostName, SSLSession session) {
                    return true;
                }
            };
            LayeredConnectionSocketFactory sslSF = new SSLConnectionSocketFactory(sslContext, hv);
            registryBuilder.register("https", sslSF);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        Registry<ConnectionSocketFactory> registry = registryBuilder.build();
        //设置连接管理器  
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(registry);
//      connManager.setDefaultConnectionConfig(connConfig);  
//      connManager.setDefaultSocketConfig(socketConfig);  
        //构建客户端  
        return HttpClientBuilder.create().setConnectionManager(connManager).build();
    }


    /**
     * 发送get请求；不带请求头和请求参数
     *
     * @param url 请求地址
     * @return
     * @throws Exception
     */
    public static HttpClientResult doGet(String url) throws Exception {
        return doGet(url, null, null, false);
    }

    /**
     * 发送get请求；带请求参数
     *
     * @param url    请求地址
     * @param params 请求参数集合
     * @return
     * @throws Exception
     */
    public static HttpClientResult doGet(String url, Map<String, String> params) throws Exception {
        return doGet(url, null, params, false);
    }

    /**
     * 发送get请求；带请求头和请求参数
     *
     * @param url     请求地址
     * @param headers 请求头集合
     * @param params  请求参数集合
     * @return
     * @throws Exception
     */
    public static HttpClientResult doGet(String url, Map<String, String> headers, Map<String, String> params, boolean hasBytes) throws Exception {
        // 创建httpClient对象
        CloseableHttpClient httpClient = getHttpClient();

        // 创建访问的地址
        URIBuilder uriBuilder = new URIBuilder(url);
        if (params != null) {
            Set<Entry<String, String>> entrySet = params.entrySet();
            for (Entry<String, String> entry : entrySet) {
                uriBuilder.setParameter(entry.getKey(), entry.getValue());
            }
        }

        // 创建http对象
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        /**
         * setConnectTimeout：设置连接超时时间，单位毫秒。
         * setConnectionRequestTimeout：设置从connect Manager(连接池)获取Connection
         * 超时时间，单位毫秒。这个属性是新加的属性，因为目前版本是可以共享连接池的。
         * setSocketTimeout：请求获取数据的超时时间(即响应时间)，单位毫秒。 如果访问一个接口，多少时间内无法返回数据，就直接放弃此次调用。
         */
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(CONNECT_TIMEOUT).setSocketTimeout(SOCKET_TIMEOUT).build();
        httpGet.setConfig(requestConfig);

        // 设置请求头
        packageHeader(headers, httpGet);

        // 创建httpResponse对象
        CloseableHttpResponse httpResponse = null;

        try {
            // 执行请求并获得响应结果
            return getHttpClientResult(httpResponse, httpClient, httpGet, hasBytes);
        } finally {
            // 释放资源
            release(httpResponse, httpClient);
        }
    }

    /**
     * 发送post请求；不带请求头和请求参数
     *
     * @param url 请求地址
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPost(String url) throws Exception {
        return doPost(url, null, null, null, false);
    }

    /**
     * 发送post请求；带请求参数
     *
     * @param url    请求地址
     * @param params 参数集合
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPost(String url, Map<String, String> params, boolean getBytes) throws Exception {
        return doPost(url, null, params, null, getBytes);
    }

    /**
     * 发送post请求；带请求参数
     *
     * @param url    请求地址
     * @param params 参数集合
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPost(String url, Map<String, String> params) throws Exception {
        return doPost(url, null, params, null, false);
    }

    /**
     * 发送post请求；带请求 body
     *
     * @param url          请求地址
     * @param postJsonData 参数BODY
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPost(String url, String postJsonData, boolean getBytes) throws Exception {
        return doPost(url, null, null, postJsonData, getBytes);
    }

    /**
     * 发送post请求；带请求 body
     *
     * @param url          请求地址
     * @param postJsonData 参数BODY
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPost(String url, String postJsonData) throws Exception {
        return doPost(url, null, null, postJsonData, false);
    }


    /**
     * 发送post请求；带请求头和请求参数
     *
     * @param url     请求地址
     * @param headers 请求头集合
     * @param params  请求参数集合
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPost(String url, Map<String, String> headers, Map<String, String> params, String postJsonData, boolean getBytes) throws Exception {
        return doPost(url, headers, params, null, postJsonData, getBytes);
    }

    public static HttpClientResult doPost(String url, Map<String, String> params, Map<String, File> fileParams, boolean getBytes) throws Exception {
        return doPost(url, null, params, fileParams, null, getBytes);
    }

    /**
     * 发送post请求；带请求头、请求参数、文件参数
     *
     * @param url
     * @param headers
     * @param params
     * @param fileParams
     * @param postJsonData
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPost(String url, Map<String, String> headers, Map<String, String> params, Map<String, File> fileParams, String postJsonData, boolean getBytes) throws Exception {
        log.info(url);
        // 创建httpClient对象
        CloseableHttpClient httpClient = getHttpClient();
//每个post参数之间的分隔。随意设定，只要不会和其他的字符串重复即可。
//        String boundary ="--------------4585696313564699";
        // 创建http对象
        HttpPost httpPost = new HttpPost(url);
//        httpPost.setHeader("Content-Type","multipart/form-data; boundary="+boundary);
        /**
         * setConnectTimeout：设置连接超时时间，单位毫秒。
         * setConnectionRequestTimeout：设置从connect Manager(连接池)获取Connection
         * 超时时间，单位毫秒。这个属性是新加的属性，因为目前版本是可以共享连接池的。
         * setSocketTimeout：请求获取数据的超时时间(即响应时间)，单位毫秒。 如果访问一个接口，多少时间内无法返回数据，就直接放弃此次调用。
         */
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(CONNECT_TIMEOUT).setSocketTimeout(SOCKET_TIMEOUT).build();
        httpPost.setConfig(requestConfig);
//        httpPost.setHeader("Content-Type","multipart/form-data");
        // 设置请求头
//        httpPost.setHeader("Cookie", "");
//        httpPost.setHeader("Connection", "keep-alive");
//        httpPost.setHeader("Accept", "application/json");
//        httpPost.setHeader("Accept-Language", "zh-CN,zh;q=0.9");
//        httpPost.setHeader("Accept-Encoding", "gzip, deflate, br");
//        httpPost.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36");
//        packageHeader(headers, httpPost);

//        setJsonParam(postJsonData, httpPost);

        setFileParam(fileParams, httpPost, params);

        // 封装请求参数
//        packageParam(params, httpPost);

        // 创建httpResponse对象
        CloseableHttpResponse httpResponse = null;
//        BasicHttpEntity en = new BasicHttpEntity();
//        en.setContentType("application/json");
//        httpPost.setEntity(en);
        try {
            // 执行请求并获得响应结果
            return getHttpClientResult(httpResponse, httpClient, httpPost, getBytes);
        } finally {
            // 释放资源
            release(httpResponse, httpClient);
        }
    }

    /**
     * 设置超时时间，调用接口
     *
     * @param url          路径
     * @param headers      header
     * @param params       表单参数
     * @param postJsonData json入参
     * @param timeout      自定义超时时间
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPost(String url, Map<String, String> headers, Map<String, String> params,
                                          String postJsonData, int timeout) throws Exception {
        // 创建httpClient对象
        CloseableHttpClient httpClient = getHttpClient();

        // 创建http对象
        HttpPost httpPost = new HttpPost(url);
        /**
         * setConnectTimeout：设置连接超时时间，单位毫秒。 setConnectionRequestTimeout：设置从connect Manager(连接池)获取Connection
         * 超时时间，单位毫秒。这个属性是新加的属性，因为目前版本是可以共享连接池的。 setSocketTimeout：请求获取数据的超时时间(即响应时间)，单位毫秒。
         * 如果访问一个接口，多少时间内无法返回数据，就直接放弃此次调用。
         */
        RequestConfig requestConfig =
                RequestConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).build();
        httpPost.setConfig(requestConfig);
        // 设置请求头
        httpPost.setHeader("Cookie", "");
        httpPost.setHeader("Connection", "keep-alive");
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Accept-Language", "zh-CN,zh;q=0.9");
        httpPost.setHeader("Accept-Encoding", "gzip, deflate, br");
        httpPost.setHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36");
        packageHeader(headers, httpPost);

        setJsonParam(postJsonData, httpPost);

        // 封装请求参数
        packageParam(params, httpPost);

        // 创建httpResponse对象
        CloseableHttpResponse httpResponse = null;

        try {
            // 执行请求并获得响应结果
            return getHttpClientResult(httpResponse, httpClient, httpPost);
        } finally {
            // 释放资源
            release(httpResponse, httpClient);
        }
    }

    /**
     * 发送put请求；不带请求参数
     *
     * @param url 请求地址
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPut(String url) throws Exception {
        return doPut(url, null, null, null);
    }

    /**
     * 发送put请求；带请求body
     *
     * @param url          请求地址
     * @param postJsonData 参数集合
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPut(String url, String postJsonData) throws Exception {
        return doPut(url, null, null, postJsonData);
    }

    public static HttpClientUtil getInstance() {
        if (httpClientUtil == null) {
            httpClientUtil = new HttpClientUtil();
        }
        return httpClientUtil;
    }

    /**
     * 发送put请求；带请求参数
     *
     * @param url    请求地址
     * @param params 参数集合
     * @return
     * @throws Exception
     */
    public static HttpClientResult doPut(String url, Map<String, String> headers, Map<String, String> params, String postJsonData) throws Exception {
        CloseableHttpClient httpClient = getHttpClient();
        HttpPut httpPut = new HttpPut(url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(CONNECT_TIMEOUT).setSocketTimeout(SOCKET_TIMEOUT).build();
        httpPut.setConfig(requestConfig);

        packageHeader(headers, httpPut);
        setJsonParam(postJsonData, httpPut);
        packageParam(params, httpPut);

        CloseableHttpResponse httpResponse = null;

        try {
            return getHttpClientResult(httpResponse, httpClient, httpPut);
        } finally {
            release(httpResponse, httpClient);
        }
    }

    /**
     * 发送delete请求；不带请求参数
     *
     * @param url 请求地址
     * @return
     * @throws Exception
     */
    public static HttpClientResult doDelete(String url) throws Exception {
        CloseableHttpClient httpClient = getHttpClient();
        HttpDelete httpDelete = new HttpDelete(url);
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(CONNECT_TIMEOUT).setSocketTimeout(SOCKET_TIMEOUT).build();
        httpDelete.setConfig(requestConfig);

        CloseableHttpResponse httpResponse = null;
        try {
            return getHttpClientResult(httpResponse, httpClient, httpDelete);
        } finally {
            release(httpResponse, httpClient);
        }
    }

    /**
     * 发送delete请求；带请求参数
     *
     * @param url    请求地址
     * @param params 参数集合
     * @return
     * @throws Exception
     */
    public static HttpClientResult doDelete(String url, Map<String, String> params) throws Exception {
        if (params == null) {
            params = new HashMap<String, String>();
        }

        params.put("_method", "delete");
        return doPost(url, params, false);
    }

    /**
     * Description: 封装请求头
     *
     * @param params
     * @param httpMethod
     */
    public static void packageHeader(Map<String, String> params, HttpRequestBase httpMethod) {
        // 封装请求头
        if (params != null) {
            Set<Entry<String, String>> entrySet = params.entrySet();
            for (Entry<String, String> entry : entrySet) {
                // 设置到请求头到HttpRequestBase对象中
                httpMethod.setHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 设置json参数
     */
    public static void setJsonParam(String json, HttpEntityEnclosingRequestBase httpMethod) {
        if (StringUtil.isEmpty(json)) {
            return;
        }
        StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
        httpMethod.setEntity(entity);
    }

    /**
     * 设置文件参数
     *
     * @param fileParams
     * @param httpMethod
     */
    public static void setFileParam(Map<String, File> fileParams, HttpEntityEnclosingRequestBase httpMethod, Map<String, String> otherParams) throws IOException {
//        if (fileParams != null) {
//        String boundary = "--------------4585696313564699";
////        // 创建http对象
//        httpMethod.setHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
//        Charset charset = Charset.forName("utf-8");
//
//        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
//        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
//        builder.setCharset(charset);
        if (fileParams != null) {
            for (String key : fileParams.keySet()) {
//                FileBody file = new FileBody(fileParams.get(key));
//                builder.addPart(key, file);
                String filePath = "d:/aa.png";
                File file = new File(filePath);
                MultipartFile multipartFile = null;
                        // 需要导入commons-fileupload的包
                FileItem fileItem = new DiskFileItem("copyfile.txt", Files.probeContentType(file.toPath()), false, file.getName(), (int) file.length(), file.getParentFile());
                byte[] buffer = new byte[4096];
                int n;
                try (InputStream inputStream = new FileInputStream(file); OutputStream os = fileItem.getOutputStream()) {
                    while ((n = inputStream.read(buffer, 0, 4096)) != -1) {
                        os.write(buffer, 0, n);
                    }
                    //也可以用IOUtils.copy(inputStream,os);
                    multipartFile = new CommonsMultipartFile(fileItem);
                    System.out.println(multipartFile.getName());
                } catch (IOException e) {
                    e.printStackTrace();
                }


                String fileName = multipartFile.getOriginalFilename();
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.setCharset(Charset.forName("utf-8"));
                builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);//加上此行代码解决返回中文乱码问题
                builder.addBinaryBody("wmImg", multipartFile.getInputStream(), ContentType.MULTIPART_FORM_DATA, fileName);// 文件流

                for (Map.Entry<String, String> e : otherParams.entrySet()) {
                    builder.addTextBody(e.getKey(), e.getValue());// 类似浏览器表单提交，对应input的name和value

                }
                HttpEntity reqEntity = builder.build();
                httpMethod.setEntity(reqEntity);
            }
        }




    }
//文件名
//        String boundary ="--------------4585696313564699";
////        // 创建http对象
//        httpMethod.setHeader("Content-Type","multipart/form-data; boundary="+boundary);
//        File file = new File("d:/watermark-add.png");
//        String fileName = file.getName();
//        //设置请求头
//
//        //HttpEntity builder
//        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
//        //字符编码
//        builder.setCharset(Charset.forName("UTF-8"));
//        //模拟浏览器
//        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
//        //boundary
////        builder.setBoundary(boundary);
//        //multipart/form-data
//        builder.addPart("multipartFile",new FileBody(file));
//        // binary
////            builder.addBinaryBody("name=\"multipartFile\"; filename=\"test.docx\"", new FileInputStream(file), ContentType.MULTIPART_FORM_DATA, fileName);// 文件流
//        //其他参数
//        builder.addTextBody("filename", fileName,  ContentType.create("text/plain", Consts.UTF_8));
//        builder.addTextBody("text", "3333333333333333",  ContentType.create("text/plain", Consts.UTF_8));
//        //HttpEntity
//        HttpEntity entity = builder.build();
//        httpMethod.setEntity(entity);


//        //创建 MultipartEntityBuilder,以此来构建我们的参数
//        MultipartEntityBuilder EntityBuilder = MultipartEntityBuilder.create();
//        EntityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
////设置字符编码，防止乱码
//        ContentType contentType=ContentType.create("text/plain",Charset.forName("UTF-8"));
////填充我们的文本内容，这里相当于input 框中的 name 与value
//        EntityBuilder.addPart("text", new StringBody("ssssss",contentType));
//        EntityBuilder.addBinaryBody("image", new File("d:/watermark-add.png"));
////参数组装
//        httpMethod.setEntity(EntityBuilder.build());
//    }


    /**
     * Description: 封装请求参数
     *
     * @param params
     * @param httpMethod
     * @throws UnsupportedEncodingException
     */
    public static void packageParam(Map<String, String> params, HttpEntityEnclosingRequestBase httpMethod)
            throws UnsupportedEncodingException {
        // 封装请求参数
        if (params != null) {
            if (params.get(OTPConstant.APP_KEY) != null) {
                String appSecret = params.get(OTPConstant.APP_KEY);
                params.remove(OTPConstant.APP_KEY);
                params.put("signMethod", SignUtil.ALGORITHM_HMAC_SHA256);
                params.put("appId", OTPConstant.APP_ID);
                params.put("timestamp", System.currentTimeMillis() + "");
                params.put("nonce", (int) (Math.random() * 101) + "");
                try {
                    String sign = SignUtil.sign(params, appSecret);
                    params.put("sign", sign);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (SignatureException e) {
                    e.printStackTrace();
                }
            }
            JSONObject obj = new JSONObject();

//            List<NameValuePair> nvps = new ArrayList<NameValuePair>();
            Set<Entry<String, String>> entrySet = params.entrySet();
            for (Entry<String, String> entry : entrySet) {
                obj.put(entry.getKey(), entry.getValue());
//                nvps.add(new BasicNameValuePair());

            }

//            List<NameValuePair> nvps = new ArrayList<NameValuePair>();
//            Set<Entry<String, String>> entrySet = params.entrySet();
//            for (Entry<String, String> entry : entrySet) {
//                nvps.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
//
//            }
//            ContentType ty = new ContentType("application/json", Charset.defaultCharset(),nvps);
            StringEntity en = new StringEntity(obj.toString());
//            en.setContentEncoding("UTF-8");
//            en.setContentType("application/json");
            // 设置到请求的http对象中
//            httpMethod.setEntity(new UrlEncodedFormEntity(nvps,"application/json", ENCODING));
            httpMethod.setEntity(en);
        }
    }

    /**
     * Description: 获得响应结果
     *
     * @param httpResponse
     * @param httpClient
     * @param httpMethod
     * @return
     * @throws Exception
     */
    private static HttpClientResult getHttpClientResult(CloseableHttpResponse httpResponse, CloseableHttpClient httpClient,
                                                        HttpRequestBase httpMethod) throws Exception {
        return getHttpClientResult(httpResponse, httpClient, httpMethod, false);
    }

    private static HttpClientResult getHttpClientResult(CloseableHttpResponse httpResponse, CloseableHttpClient httpClient,
                                                        HttpRequestBase httpMethod, boolean getBytes) throws Exception {
        // 执行请求
        httpResponse = httpClient.execute(httpMethod);
        // 获取返回结果
        if (httpResponse != null && httpResponse.getStatusLine() != null) {
            String content = "";
            byte[] contentBytes = null;

            Header[] headers = httpResponse.getHeaders("Set-Cookie");
            String cookie = null;
            if (headers.length > 0) {
                cookie = headers[0].toString();
            }
            HttpEntity en = httpResponse.getEntity();
            if (en != null) {
//                if (en.getContentType().getValue().equals("image/jpeg;charset=utf-8")) {
//                    InputStream inputStream =  en.getContent();
//                    FileUtils.copyInputStreamToFile(inputStream,new File("d:/5200/aa.jpg"));
//                } else {
//
//                }
                if (getBytes) {
                    contentBytes = EntityUtils.toByteArray(en);
                } else {
                    content = EntityUtils.toString(en, ENCODING);
                }

            }
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            HttpClientResult httpResult = new HttpClientResult(statusCode, content);
            httpResult.setContentBytes(contentBytes);
            httpResult.setCookie(cookie);
            return httpResult;
        }
        return new HttpClientResult(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }


    /**
     * Description: 释放资源
     *
     * @param httpResponse
     * @param httpClient
     * @throws IOException
     */
    public static void release(CloseableHttpResponse httpResponse, CloseableHttpClient httpClient) throws IOException {
        // 释放资源
        if (httpResponse != null) {
            httpResponse.close();
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }
}
