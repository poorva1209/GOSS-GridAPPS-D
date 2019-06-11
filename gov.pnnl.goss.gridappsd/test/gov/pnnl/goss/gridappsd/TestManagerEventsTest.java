package gov.pnnl.goss.gridappsd;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import gov.pnnl.goss.gridappsd.api.AppManager;
import gov.pnnl.goss.gridappsd.api.ConfigurationManager;
import gov.pnnl.goss.gridappsd.api.DataManager;
import gov.pnnl.goss.gridappsd.api.LogManager;
import gov.pnnl.goss.gridappsd.api.SimulationManager;
import gov.pnnl.goss.gridappsd.api.TestManager;
import gov.pnnl.goss.gridappsd.dto.LogMessage;
import gov.pnnl.goss.gridappsd.dto.RequestTestUpdate;
import gov.pnnl.goss.gridappsd.dto.TestConfig;
import gov.pnnl.goss.gridappsd.dto.events.Event;
import gov.pnnl.goss.gridappsd.testmanager.ProcessEvents;
import gov.pnnl.goss.gridappsd.testmanager.TestManagerImpl;
import pnnl.goss.core.Client;
import pnnl.goss.core.ClientFactory;

@RunWith(MockitoJUnitRunner.class)
public class TestManagerEventsTest {

	TestManager tm = new TestManagerImpl();

	@Mock
	AppManager appManager;

	@Mock
	ClientFactory clientFactory;

	@Mock
	Client client;

	@Mock
	ConfigurationManager configurationManager;

	@Mock
	private DataManager dataManager;

	@Mock
	DataManager DataManager;

	@Mock
	SimulationManager simulationManager;

	@Mock
	LogManager logManager;

	@Captor
	ArgumentCaptor<String> argCaptor;

	@Captor
	ArgumentCaptor<LogMessage> argCaptorLogMessage;
	
	ProcessEvents processEvent;
	
	@Before
	public void beforeTests(){
		List<Event> events = new ArrayList<Event>();
		processEvent = new ProcessEvents(logManager, events);
	}

	@Test
	public void testAddEvents() {
			
			String testCfg1 = "{\"events\": [{\"allOutputOutage\": true, \"allInputOutage\": true, \"inputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"outputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"event_type\": \"CommOutage\", \"occuredDateTime\": 1248130819, \"stopDateTime\": 1248130824}, {\"impedance\": \"rLineToLine\", \"PhaseConnectedFaultKind\": {\"lineToGround\": 0.2, \"lineToLine\": 0.3}, \"equipmentMrid\": \"235242342342342\", \"phases\": \"ABC\", \"event_type\": \"Fault\", \"occuredDateTime\": 1248130809, \"stopDateTime\": 1248130816}], \"appId\": \"sample_app\"}";
			TestConfig tc = TestConfig.parse(testCfg1);
			System.out.println(tc.getEvents().toString());
			
			JsonObject status = processEvent.getStatusJson();
			assertEquals(status.get("data").getAsJsonArray().size(),0);
			System.out.println(status.get("data").getAsJsonArray().size()==0);
			
			processEvent.addEvents(tc.getEvents());
			status = processEvent.getStatusJson();
			JsonArray dataArray = status.get("data").getAsJsonArray();
			assertEquals(dataArray.size(),2);
			System.out.println(status.toString());
			
			assertEquals(dataArray.get(0).getAsJsonObject().get("status").getAsString(),"SCHEDULED");
			assertEquals(dataArray.get(1).getAsJsonObject().get("status").getAsString(),"SCHEDULED");
	}

	@Test
	public void testProcessEvents() {
			
			String testCfg1 = "{\"events\": [{\"allOutputOutage\": true, \"allInputOutage\": true, \"inputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"outputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"event_type\": \"CommOutage\", \"occuredDateTime\": 1248130819, \"stopDateTime\": 1248130824}, {\"impedance\": \"rLineToLine\", \"PhaseConnectedFaultKind\": {\"lineToGround\": 0.2, \"lineToLine\": 0.3}, \"equipmentMrid\": \"235242342342342\", \"phases\": \"ABC\", \"event_type\": \"Fault\", \"occuredDateTime\": 1248130809, \"stopDateTime\": 1248130816}], \"appId\": \"sample_app\"}";
			TestConfig tc = TestConfig.parse(testCfg1);
			
			processEvent.addEvents(tc.getEvents());
			JsonObject status = processEvent.getStatusJson();
			System.out.println(status.toString());
			
	        long start_time = 1248130809 - 19;
	        long current_time = start_time;
	        long duration = 120;
	        JsonArray dataArray = null;
	        while (current_time <= start_time + duration){
	        	current_time+=3;
		        processEvent.processAtTime(client, "1234",current_time);
				status = processEvent.getStatusJson();
//				System.out.println(status.toString());
				dataArray = status.get("data").getAsJsonArray();
	        }
			assertEquals(dataArray.get(0).getAsJsonObject().get("status").getAsString(),"CLEARED");
			assertEquals(dataArray.get(1).getAsJsonObject().get("status").getAsString(),"CLEARED");
			
	}
	
	@Test
	public void testCommand(){
		
//		String testReq = "{\"power_system_config\": {\"GeographicalRegion_name\": \"_24809814-4EC6-29D2-B509-7F8BFB646437\", \"SubGeographicalRegion_name\": \"_1CD7D2EE-3C91-3248-5662-A43EFEFAC224\", \"Line_name\": \"_C1C3E687-6FFD-C753-582B-632A27E28507\"}, \"application_config\": {\"applications\": [{\"name\": \"sample_app\", \"config_string\": \"\"}]}, \"simulation_config\": {\"start_time\": \"1248130800\", \"duration\": \"40\", \"simulator\": \"GridLAB-D\", \"timestep_frequency\": \"1000\", \"timestep_increment\": \"1000\", \"run_realtime\": true, \"simulation_name\": \"ieee123\", \"power_flow_solver_method\": \"NR\", \"model_creation_config\": {\"load_scaling_factor\": \"1\", \"schedule_name\": \"ieeezipload\", \"z_fraction\": \"0\", \"i_fraction\": \"1\", \"p_fraction\": \"0\", \"randomize_zipload_fractions\": false, \"use_houses\": false}}, \"test_config\": {\"events\": [{\"allOutputOutage\": true, \"allInputOutage\": true, \"inputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"outputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"event_type\": \"CommOutage\", \"occuredDateTime\": 1557439240706, \"stopDateTime\": 0}, {\"impedance\": \"rLineToLine\", \"PhaseConnectedFaultKind\": {\"lineToGround\": 0.2, \"lineToLine\": 0.3}, \"equipmentMrid\": \"235242342342342\", \"phases\": \"ABC\", \"event_type\": \"Fault\", \"occuredDateTime\": 1557439240708, \"stopDateTime\": 1557439240708}], \"appId\": \"sample_app\"}}";
//		String testReq = "{\"events\": [{\"allOutputOutage\": true, \"allInputOutage\": true, \"inputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"outputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"event_type\": \"CommOutage\", \"occuredDateTime\": 1248130819, \"stopDateTime\": 1248130824}, {\"PhaseConnectedFaultKind\": \"lineToGround\", \"FaultImpedance\": {\"rGround\": 0.001, \"xGround\": 0.001}, \"ObjectMRID\": \"235242342342342\", \"phases\": \"ABC\", \"event_type\": \"Fault\", \"occuredDateTime\": 1248130809, \"stopDateTime\": 1248130816}], \"command\": \"query_events\"}";
		String request_update = "{\"events\": [{\"faultMRID\": \"12312312\", \"occuredDateTime\": 1248130809, \"stopDateTime\": 1248130824}], \"command\": \"update_events\"}";
		
		RequestTestUpdate reqTest1 = RequestTestUpdate.parse(request_update);
		System.out.println(reqTest1.toString());
		System.out.println(reqTest1.getCommand());
		assertEquals(reqTest1.getCommand().toString(), "update_events");
		
		String request_status = "{\"command\": \"query_events\"}";
		RequestTestUpdate reqTest2 = RequestTestUpdate.parse(request_status);
		System.out.println(reqTest2.getCommand());
		assertEquals(reqTest2.getCommand().toString(), "query_events");
	}
	
	@Test
	public void testUpdateEvents() {
			
			String testCfg1 = "{\"events\": [{\"allOutputOutage\": true, \"allInputOutage\": true, \"inputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"outputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"event_type\": \"CommOutage\", \"occuredDateTime\": 1248130819, \"stopDateTime\": 1248130824}, {\"impedance\": \"rLineToLine\", \"PhaseConnectedFaultKind\": {\"lineToGround\": 0.2, \"lineToLine\": 0.3}, \"equipmentMrid\": \"235242342342342\", \"phases\": \"ABC\", \"event_type\": \"Fault\", \"occuredDateTime\": 1248130809, \"stopDateTime\": 1248130816}], \"appId\": \"sample_app\"}";
			TestConfig tc = TestConfig.parse(testCfg1);
			
			processEvent.addEvents(tc.getEvents());
			JsonObject status = processEvent.getStatusJson();
			JsonArray dataArray0 = status.get("data").getAsJsonArray();
			String faultMRID = dataArray0.get(0).getAsJsonObject().get("faultMRID").getAsString();
			Long occuredDateTime = dataArray0.get(0).getAsJsonObject().get("occuredDateTime").getAsLong();
			Long stopDateTime = dataArray0.get(0).getAsJsonObject().get("stopDateTime").getAsLong();
			occuredDateTime-=6;
			stopDateTime-=6;
			System.out.println(faultMRID + " " +occuredDateTime + " "+ stopDateTime);
			
//			String request_update = "{\"events\": [{\"faultMRID\": \"12312312\", \"occuredDateTime\": 1248130809, \"stopDateTime\": 1248130824}], \"command\": \"update_events\"}";
			String request_update = "{\"events\": [{\"faultMRID\": \""+faultMRID +"\", \"occuredDateTime\": "+occuredDateTime+", \"stopDateTime\": "+stopDateTime+"}], \"command\": \"update_events\"}";
			
			RequestTestUpdate reqTest1 = RequestTestUpdate.parse(request_update);
			System.out.println(reqTest1.toString());
			assertEquals(reqTest1.getCommand().toString(), "update_events");
			
			processEvent.updateEventTimes(reqTest1.getEvents());
			
			JsonObject status1 = processEvent.getStatusJson();
			JsonArray dataArray1 = status1.get("data").getAsJsonArray();
			String faultMRID1 = dataArray1.get(0).getAsJsonObject().get("faultMRID").getAsString();
			Long occuredDateTime1 = dataArray1.get(0).getAsJsonObject().get("occuredDateTime").getAsLong();
			Long stopDateTime1 = dataArray1.get(0).getAsJsonObject().get("stopDateTime").getAsLong();
			assertEquals(faultMRID, faultMRID1);
			assertEquals(occuredDateTime, occuredDateTime1);
			assertEquals(stopDateTime, stopDateTime1);
			
	        long start_time = 1248130809 - 19;
	        long current_time = start_time;
	        long duration = 120;
	        JsonArray dataArray = null;
	        while (current_time <= start_time + duration){
	        	current_time+=3;
		        processEvent.processAtTime(client, "1234",current_time);
				status = processEvent.getStatusJson();
				System.out.println(status.toString());
				dataArray = status.get("data").getAsJsonArray();
	        }
			assertEquals(dataArray.get(0).getAsJsonObject().get("status").getAsString(),"CLEARED");
			assertEquals(dataArray.get(1).getAsJsonObject().get("status").getAsString(),"CLEARED");
			
			
			RequestTestUpdate rt = RequestTestUpdate.parse(testCfg1);
			System.out.println(tc.getEvents().toString());
			
	//		String testReq = "{\"power_system_config\": {\"GeographicalRegion_name\": \"_24809814-4EC6-29D2-B509-7F8BFB646437\", \"SubGeographicalRegion_name\": \"_1CD7D2EE-3C91-3248-5662-A43EFEFAC224\", \"Line_name\": \"_C1C3E687-6FFD-C753-582B-632A27E28507\"}, \"application_config\": {\"applications\": [{\"name\": \"sample_app\", \"config_string\": \"\"}]}, \"simulation_config\": {\"start_time\": \"1248130800\", \"duration\": \"40\", \"simulator\": \"GridLAB-D\", \"timestep_frequency\": \"1000\", \"timestep_increment\": \"1000\", \"run_realtime\": true, \"simulation_name\": \"ieee123\", \"power_flow_solver_method\": \"NR\", \"model_creation_config\": {\"load_scaling_factor\": \"1\", \"schedule_name\": \"ieeezipload\", \"z_fraction\": \"0\", \"i_fraction\": \"1\", \"p_fraction\": \"0\", \"randomize_zipload_fractions\": false, \"use_houses\": false}}, \"test_config\": {\"events\": [{\"allOutputOutage\": true, \"allInputOutage\": true, \"inputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"outputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"event_type\": \"CommOutage\", \"occuredDateTime\": 1557439240706, \"stopDateTime\": 0}, {\"impedance\": \"rLineToLine\", \"PhaseConnectedFaultKind\": {\"lineToGround\": 0.2, \"lineToLine\": 0.3}, \"equipmentMrid\": \"235242342342342\", \"phases\": \"ABC\", \"event_type\": \"Fault\", \"occuredDateTime\": 1557439240708, \"stopDateTime\": 1557439240708}], \"appId\": \"sample_app\"}}";
	//		String testReq = "{\"events\": [{\"allOutputOutage\": true, \"allInputOutage\": true, \"inputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"outputOutageList\": [\"mrid1123457899\", \"mrid234578908\"], \"event_type\": \"CommOutage\", \"occuredDateTime\": 1248130819, \"stopDateTime\": 1248130824}, {\"PhaseConnectedFaultKind\": \"lineToGround\", \"FaultImpedance\": {\"rGround\": 0.001, \"xGround\": 0.001}, \"ObjectMRID\": \"235242342342342\", \"phases\": \"ABC\", \"event_type\": \"Fault\", \"occuredDateTime\": 1248130809, \"stopDateTime\": 1248130816}], \"command\": \"query_events\"}";

	}

}