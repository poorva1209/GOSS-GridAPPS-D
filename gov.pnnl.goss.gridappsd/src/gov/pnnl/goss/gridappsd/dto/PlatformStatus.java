package gov.pnnl.goss.gridappsd.dto;

import java.io.Serializable;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;



public class PlatformStatus implements Serializable{

	private static final long serialVersionUID = 285312877963778626L;
	
	List<AppInfo> applications;
	List<ServiceInfo> services;
	List<AppInstance> appInstances;
	List<ServiceInstance> serviceInstances;
	
	
	public PlatformStatus() {}
	
	
	public PlatformStatus(List<AppInfo> applications,
			List<ServiceInfo> services, List<AppInstance> appInstances,
			List<ServiceInstance> serviceInstances) {
		super();
		this.applications = applications;
		this.services = services;
		this.appInstances = appInstances;
		this.serviceInstances = serviceInstances;
	}
	
	public List<AppInfo> getApplications() {
		return applications;
	}
	public void setApplications(List<AppInfo> applications) {
		this.applications = applications;
	}
	public List<ServiceInfo> getServices() {
		return services;
	}
	public void setServices(List<ServiceInfo> services) {
		this.services = services;
	}
	public List<AppInstance> getAppInstances() {
		return appInstances;
	}
	public void setAppInstances(List<AppInstance> appInstances) {
		this.appInstances = appInstances;
	}
	public List<ServiceInstance> getServiceInstances() {
		return serviceInstances;
	}
	public void setServiceInstances(List<ServiceInstance> serviceInstances) {
		this.serviceInstances = serviceInstances;
	}
	@Override
	public String toString() {
		Gson  gson = new Gson();
		return gson.toJson(this);
	}
	
}
