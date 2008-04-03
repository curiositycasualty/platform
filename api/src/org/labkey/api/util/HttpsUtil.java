package org.labkey.api.util;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;

/**
 * User: jeckels
 * Date: Nov 22, 2006
 */
public class HttpsUtil
{

    private static SSLSocketFactory _socketFactory;
    private static HostnameVerifier _hostnameVerifier = new HostnameVerifier()
    {
        public boolean verify(String urlHostName, SSLSession session)
        {
            return true;
        }
    };

    /**
     * Disables host name validation, as well as certificate trust chain
     * validation, on the connection. This will let Java connect to self-signed
     * certs over SSL without complaint, as well as to SSL certs that
     * don't match the URL (for example, connecting to "localhost" when the
     * SSL for localhost says it's for the server "labkey.com".
     */
    public static void disableValidation(HttpsURLConnection sslConnection)
    {
        sslConnection.setHostnameVerifier(_hostnameVerifier);
        sslConnection.setSSLSocketFactory(getSocketFactory());
    }

    // Create a socket factory that does not validate the server's certificate -
    // all we care about is that the connection is encrypted if it's going over SSL
    private synchronized static SSLSocketFactory getSocketFactory()
    {
        if (_socketFactory == null)
        {
            //Create a trust manager that does not validate certificate chains:
            TrustManager[] trustAllCerts = new TrustManager[]
                {
                    new X509TrustManager()
                    {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers()
                        {
                            return new java.security.cert.X509Certificate[0];
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] x509Certificates, String string)
                        {}

                        public void checkServerTrusted(java.security.cert.X509Certificate[] x509Certificates, String string)
                        {}
                    }
                };

            try
            {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new SecureRandom());
                _socketFactory = sc.getSocketFactory();
            }
            catch (NoSuchAlgorithmException e)
            {
                throw new RuntimeException(e);
            }
            catch (KeyManagementException e)
            {
                throw new RuntimeException(e);
            }
        }
        return _socketFactory;
    }
}
