package gov.pnnl.goss.gridappsd.api;

public interface PowerGridModelDataManager {
	
	//resultFormat is optional
	void query(String query, String responseFormat, String resultTopic, String statusTopic);

	//resultFormat is optional
	void queryObject(String mrid, String responseFormat, String outputTopic, String statusTopic);

	//modelId and resultFormat are optional
	void queryObjectTypeList(String modelId, String responseFormat, String resultTopic, String statusTopic);

	//ObjectType , filter and resultFormat are optional
	//filter can be SPARQL syntax
	void queryModel(String modelId, String ObjectType, String filter, String responseFormat, String outputTopic, String statusTopic);

	void queryModelList(String outputTopic, String statusTopic);

}
