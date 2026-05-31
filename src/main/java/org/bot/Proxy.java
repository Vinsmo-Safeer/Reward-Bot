package org.bot;

public class Proxy {

    private boolean staticProxy;
    private final String proxyAddress; // Made final since it identifies this specific proxy
    private String port;
    private String status;
    private String countryCode;
    private double usageLeft; // In GB

    public Proxy(String proxyAddress) {
        this.proxyAddress = proxyAddress;
    }
    public String getProxyAddress() {
        return proxyAddress; // No need to fetch details just to know the IP address itself
    }

    public void setStaticProxy(boolean staticProxy) {
        this.staticProxy = staticProxy;
    }
    public boolean isStaticProxy() {
        return staticProxy;
    }

    public void setPort(String port) {
        this.port = port;
    }
    public String getPort() {
        return port;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public String getStatus() {
        return status;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
    public String getCountryCode() {
        return countryCode;
    }

    public void setUsageLeft(double usageLeft) {
        this.usageLeft = usageLeft;
    }
    public double getUsageLeft() {
        return usageLeft;
    }
}