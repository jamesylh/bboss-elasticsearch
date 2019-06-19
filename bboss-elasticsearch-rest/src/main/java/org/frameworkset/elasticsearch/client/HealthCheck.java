package org.frameworkset.elasticsearch.client;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.frameworkset.elasticsearch.ElasticSearchSinkConstants;
import org.frameworkset.spi.BaseApplicationContext;
import org.frameworkset.spi.remote.http.HttpRequestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * es节点健康检查
 */
public class HealthCheck implements Runnable{
	private List<ESAddress> esAddresses;

	private static Logger logger = LoggerFactory.getLogger(HealthCheck.class);
	private long checkInterval = 5000;
	private List<HCRunable> checkThreads ;
	private Map<String, String> headers;
	public HealthCheck(List<ESAddress> esAddresses,long checkInterval,Map<String, String> headers){
		this.esAddresses = esAddresses;
		this.checkInterval = checkInterval;
		this.headers = headers;

	}
	public void stopCheck(){
		HCRunable t = null;
		for(int i = 0; i < checkThreads.size(); i ++){
			t = checkThreads.get(i);
			t.stopRun();
		}
	}

	public void checkNewAddresses(List<ESAddress> addresses) {
		HCRunable t = null;
		for(int i = 0; i < addresses.size(); i ++){
			ESAddress address = addresses.get(i);
			t = new HCRunable(address);
			t.start();
			checkThreads.add(t);
		}
	}

	class HCRunable extends Thread {
		ESAddress address;
		boolean stop = false;
		public HCRunable(ESAddress address){
			super("Elasticsearch server["+address.toString()+"] health check");
			address.setHealthCheck(this);
			this.address = address;
		}
		public void stopRun(){
			this.stop = true;
			this.interrupt();
		}
		@Override
		public void run() {
			 while (true){
			 	if(this.stop)
			 		break;
			 	 if(address.failedCheck()){
			 		 try {		
			 			 if(logger.isDebugEnabled())
			 				 logger.debug(new StringBuilder().append("Check downed elasticsearch server[").append(address.toString()).append("] status.").toString());
						 HttpRequestUtil.httpGet(ElasticSearchSinkConstants.healthCheckHttpPool,address.getHealthPath(),headers,new ResponseHandler<Void>(){
	
							 @Override
							 public Void handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
								 int status = response.getStatusLine().getStatusCode();
								 if (status >= 200 && status < 300) {
									 if(logger.isInfoEnabled())
										 logger.info(new StringBuilder().append("Downed elasticsearch server[").append(address.toString()).append("] recovered to normal server.").toString());
									 address.onlySetStatus(0);
								 } else {
									address.onlySetStatus(1);
								 }
								 return null;
							 }
						 });
					
					 } catch (Exception e) {
						 if(logger.isInfoEnabled())
							 logger.info(new StringBuilder().append("Down elasticsearch node health check use [").append(address.getHealthPath()).append("] failed:").append(" Elasticsearch server[").append(address.toString()).append("] is down.").toString());
						 address.onlySetStatus(1);
					 }
			 		 if(this.stop)
					 		break;
					 try {
						 sleep(checkInterval);
					 } catch (InterruptedException e) {					 
						 break;
					 }
			 	 }
			 	 else{
			 		 try {
			 			 synchronized(this){
			 				 wait();
			 			 }
					} catch (InterruptedException e) {
						break;
					}
			 	 }
				
			 }
		}
	}
	@Override
	public void run() {
		Iterator<ESAddress> iterable = this.esAddresses.iterator();
		checkThreads = new ArrayList<HCRunable>();
		HCRunable t = null;
		ESAddress address = null;
		while(iterable.hasNext()){
			address = iterable.next();
			t = new HCRunable(address);
			t.start();
			checkThreads.add(t);
		}
		BaseApplicationContext.addShutdownHook(new Runnable() {
			@Override
			public void run() {
				stopCheck();
			}
		});
	}

	public void addNewAddress(List<ESAddress> addresses){
		Iterator<ESAddress> iterable = addresses.iterator();
		HCRunable t = null;
		ESAddress address = null;
		while(iterable.hasNext()){
			address = iterable.next();
			t = new HCRunable(address);
			t.start();
			checkThreads.add(t);
		}
	}

}
