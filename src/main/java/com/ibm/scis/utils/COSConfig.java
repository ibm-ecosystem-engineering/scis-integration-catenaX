package com.ibm.scis.utils;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.auth.BasicAWSCredentials;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

@Configuration
public class COSConfig {

	@Value("${ibm.cos.endpoint}")
	private String endpoint;

	@Value("${ibm.cos.hmac-access-key}")
	private String hmacAccessKey;

	@Value("${ibm.cos.hmac-secret-key}")
	private String hmacSecretKey;

	@Bean
	public AmazonS3 cosClient() throws Exception {
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(hmacAccessKey, hmacSecretKey);
		ClientConfiguration clientConfig = new ClientConfiguration().withRequestTimeout(5000);

		InputStream certInputStream = getClass().getClassLoader().getResourceAsStream("ibm-cos-cert.cer");
		if (certInputStream == null) {
			throw new RuntimeException("Certificate file not found in resources folder");
		}

		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		X509Certificate cert = (X509Certificate) cf.generateCertificate(certInputStream);

		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		keyStore.load(null, null);
		keyStore.setCertificateEntry("ibm-cos-cert", cert);

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, new javax.net.ssl.TrustManager[] { new javax.net.ssl.X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return new java.security.cert.X509Certificate[] { cert };
			}

			public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}
		} }, new java.security.SecureRandom());

		SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext,
				NoopHostnameVerifier.INSTANCE);
		clientConfig.getApacheHttpClientConfig().setSslSocketFactory(sslSocketFactory);

		return AmazonS3ClientBuilder.standard()
				.withEndpointConfiguration(new AmazonS3ClientBuilder.EndpointConfiguration(endpoint, null))
				.withCredentials(new AWSStaticCredentialsProvider(awsCreds)).withPathStyleAccessEnabled(true)
				.withClientConfiguration(clientConfig).build();
	}
}