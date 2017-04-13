package pnnl.goss.gridappsd.api;

public interface GridAPPSDService {
	
	boolean start();
	
	boolean getStatus();
	
	boolean stop();
	
	boolean canStartExternally();
	
	boolean canStopExternally();
	
	String getServiceOutputTopic();
	
	String getServiceInputTopic();
	
}
