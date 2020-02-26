package gov.pnnl.goss.gridappsd.simulation;

import gov.pnnl.goss.gridappsd.api.AppManager;
import gov.pnnl.goss.gridappsd.api.LogManager;
import gov.pnnl.goss.gridappsd.api.ServiceManager;
import gov.pnnl.goss.gridappsd.configuration.GLDAllConfigurationHandler;
import gov.pnnl.goss.gridappsd.dto.FncsBridgeResponse;
import gov.pnnl.goss.gridappsd.dto.LogMessage;
import gov.pnnl.goss.gridappsd.dto.LogMessage.LogLevel;
import gov.pnnl.goss.gridappsd.dto.LogMessage.ProcessStatus;
import gov.pnnl.goss.gridappsd.dto.SimulationConfig;
import gov.pnnl.goss.gridappsd.dto.SimulationContext;
import gov.pnnl.goss.gridappsd.utils.GridAppsDConstants;
import gov.pnnl.goss.gridappsd.utils.RunCommandLine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pnnl.goss.core.Client;
import pnnl.goss.core.DataResponse;
import pnnl.goss.core.GossResponseEvent;
import pnnl.goss.core.security.SecurityConfig;

import com.google.gson.Gson;

public class SimulationProcess extends Thread {
    private static Logger log = LoggerFactory.getLogger(SimulationProcess.class);


    boolean running = true;

    public boolean isRunning() {
        return running;
    }
    public void setRunning(boolean running) {
        this.running = running;
    }


    class InitializedTracker {
        public boolean isInited = false;
    }


    class SimulationTracker {
        public boolean isFinished = false;
    }

    SimulationContext simContext;
    ServiceManager serviceManager;
    SimulationConfig simulationConfig;
    int simulationId;
    LogManager logManager;
    AppManager appManager;
    Client client;
    SecurityConfig securityConfig;

    public SimulationProcess(SimulationContext simContext, ServiceManager serviceManager,
            SimulationConfig simulationConfig, int simulationId, LogManager logManager,
            AppManager appManager, Client client, SecurityConfig securityConfig){
        this.simContext = simContext;
        this.serviceManager = serviceManager;
        this.simulationConfig = simulationConfig;
        this.simulationId = simulationId;
        this.logManager = logManager;
        this.appManager = appManager;
        this.client = client;
        this.securityConfig = securityConfig;
    }



    @Override
    public void run() {

        Process simulatorProcess = null;
        InitializedTracker isInitialized = new InitializedTracker();
        SimulationTracker isFinished = new SimulationTracker();
        LogMessage logMessage = new LogMessage(this.getClass().getSimpleName(),
                    Integer.toString(simulationId),
                    new Date().getTime(),
                    null,
                    LogLevel.INFO,
                    ProcessStatus.RUNNING,
                    true);
        
        
        try{

        	File simulationFile = new File(simContext.getStartupFile());

            if(simulationConfig!=null && simulationConfig.model_creation_config!=null && simulationConfig.model_creation_config.schedule_name!=null && simulationConfig.model_creation_config.schedule_name.trim().length()>0){
                File serviceDir = serviceManager.getServiceConfigDirectory();
                /*try{
                    RunCommandLine.runCommand("cp "+serviceDir.getAbsolutePath()+File.separator+"etc"+File.separator+"zipload_schedule.player "+simulationFile.getParentFile().getAbsolutePath()+File.separator+simulationConfig.model_creation_config.schedule_name+".player");
                }catch(Exception e){
                    log.warn("Could not copy player file to working directory");
                }*/
                try{
                    RunCommandLine.runCommand("cp "+serviceDir.getAbsolutePath()+File.separator+"etc"+File.separator+"appliance_schedules.glm "+simulationFile.getParentFile().getAbsolutePath()+File.separator+GLDAllConfigurationHandler.SCHEDULES_FILENAME);
                }catch(Exception e){
                    log.warn("Could not copy schedules file to working directory");
                }
            }

            //Start GridLAB-D
            logMessage.setLogMessage(simContext.getSimulatorPath()+" "+simulationFile);
            logMessage.setTimestamp(new Date().getTime());
            logManager.log(logMessage,simContext.getSimulationUser(),
                    GridAppsDConstants.topic_simulationLog+simulationId);
            ProcessBuilder simulatorBuilder = new ProcessBuilder(simContext.getSimulatorPath(), simulationFile.getAbsolutePath());
            simulatorBuilder.redirectErrorStream(true);
            simulatorBuilder.redirectOutput();
            //launch from directory containing simulation files
            simulatorBuilder.directory(simulationFile.getParentFile());
            simulatorProcess = simulatorBuilder.start();
            // Watch the process
            watch(simulatorProcess, "Simulator-"+simulationId);


            //TODO: check if GridLAB-D is started correctly and send publish simulation status accordingly
            logMessage.setLogMessage("GridLAB-D started");
            logMessage.setTimestamp(new Date().getTime());
            logManager.log(logMessage,simContext.getSimulationUser(),
                    GridAppsDConstants.topic_simulationLog+simulationId);

            //Subscribe to fncs-goss-bridge output topic
            GossFncsResponseEvent gossFncsResponseEvent = new GossFncsResponseEvent(logManager, isInitialized, isFinished, simulationId);
            client.subscribe(GridAppsDConstants.topic_FNCS_output, gossFncsResponseEvent);
            
            
            logMessage.setLogMessage("Checking fncs is initialized, currently "+isInitialized.isInited);
            logMessage.setTimestamp(new Date().getTime());
            logManager.log(logMessage,simContext.getSimulationUser(),
                    GridAppsDConstants.topic_simulationLog+simulationId);
            
            int initAttempts = 0;
            while(!isInitialized.isInited && initAttempts<SimulationManagerImpl.MAX_INIT_ATTEMPTS){
                //Send 'isInitialized' call to fncs-goss-bridge to check initialization until it is initialized.
                //TODO add limiting how long it checks for initialized, or cancel if the fncs process exits
                //This call would return true/false for initialization and simulation output of time step 0.
                
                client.publish(GridAppsDConstants.topic_FNCS_input, "{\"command\": \"isInitialized\"}");
                initAttempts++;
                Thread.sleep(1000);

            }

            if(initAttempts<SimulationManagerImpl.MAX_INIT_ATTEMPTS){
            	logMessage.setLogMessage("FNCS Initialized");
                logMessage.setTimestamp(new Date().getTime());
            	logManager.log(logMessage,simContext.getSimulationUser(),
                        GridAppsDConstants.topic_simulationLog+simulationId);

                //Send the start simulation command to the fncsgossbridge
                startSimulation(gossFncsResponseEvent, simulationConfig, simulationId);
                while(!isFinished.isFinished){
                	logMessage.setLogMessage("Checking if FNCS simulation is finished, currently "+isFinished.isFinished);
                    logMessage.setTimestamp(new Date().getTime());
                    logMessage.setLogLevel(LogLevel.DEBUG);
                	logManager.log(logMessage,simContext.getSimulationUser(),
                            GridAppsDConstants.topic_simulationLog+simulationId);
                    Thread.sleep(1000);
                }
            } else {
            	logMessage.setLogMessage("FNCS Initialization Failed");
                logMessage.setTimestamp(new Date().getTime());
                logMessage.setLogLevel(LogLevel.ERROR);
                logMessage.setProcessStatus(ProcessStatus.ERROR);
            	logManager.log(logMessage,simContext.getSimulationUser(),
                        GridAppsDConstants.topic_simulationLog+simulationId);
            }

            //call to stop the fncs broker
            client.publish(GridAppsDConstants.topic_FNCS_input, "{\"command\":  \"stop\"}");
            
            logMessage.setLogMessage("Simulation "+simulationId+" complete");
            logMessage.setTimestamp(new Date().getTime());
            logMessage.setLogLevel(LogLevel.INFO);
            logMessage.setProcessStatus(ProcessStatus.COMPLETE);
        	logManager.log(logMessage,simContext.getSimulationUser(),
                    GridAppsDConstants.topic_simulationLog+simulationId);            
        }
        catch(Exception e){
                log.error("Error during simulation",e);
                try {
                	logMessage.setLogMessage("Simulation error: "+e.getMessage());
                    logMessage.setTimestamp(new Date().getTime());
                    logMessage.setLogLevel(LogLevel.ERROR);
                    logMessage.setProcessStatus(ProcessStatus.ERROR);
                	logManager.log(logMessage,simContext.getSimulationUser(),
                            GridAppsDConstants.topic_simulationLog+simulationId);
                } catch (Exception e1) {
                    log.error("Error while reporting error status", e);
                }
        } finally {
            //Shut down applications and services connected with the simulation
            List<String> ids = simContext.getServiceInstanceIds();
            simulatorProcess.destroy();
            try {
                simulatorProcess.waitFor(10, TimeUnit.MILLISECONDS);
            } catch(InterruptedException ex) {
                simulatorProcess.destroyForcibly();
            }
            for(String id : ids){
                serviceManager.stopServiceInstance(id);
            }
            ids = simContext.getAppInstanceIds();
            for(String id : ids){
                appManager.stopAppInstance(id);
            }
        }
    }


    private void startSimulation(GossFncsResponseEvent gossEvent, SimulationConfig simulationConfig, int simulationId) throws Exception{
        // Send the start simulation command to the fncsgossbridge so that it runs it's time loop to move the fncs simulation forward
        logManager.log(new LogMessage(this.getClass().getSimpleName(),
                Integer.toString(simulationId),
                new Date().getTime(),
                "Sending start simulation to bridge.",
                LogLevel.DEBUG,
                ProcessStatus.RUNNING,
                true),simContext.getSimulationUser(),
                GridAppsDConstants.topic_platformLog);
        String message = "{\"command\": \"StartSimulation\"}";
        client.publish(GridAppsDConstants.topic_FNCS_input, message);
    }

    private void watch(final Process process, String processName) {
        new Thread() {
            public void run() {
            	LogMessage logMessage = new LogMessage(this.getClass().getName(),
                		processName, 
                		new Date().getTime(), 
                		null, 
                		LogLevel.ERROR, 
                		ProcessStatus.RUNNING, 
                		true);
            	
                try(BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))){
                	String line = null;
                    try {
                        while ((line = input.readLine()) != null) {
                        	if(!line.trim().isEmpty()){
                        		LogLevel level = LogLevel.INFO;
                        		if(line.contains("DEBUG"))
                        			level = LogLevel.DEBUG;
                        		else if(line.contains("ERROR"))
                        			level = LogLevel.ERROR;
                        		else if(line.contains("FATAL") && !line.contains("INFO"))
                        			level = LogLevel.FATAL;
                        		else if(line.contains("WARN"))
                        			level = LogLevel.WARN;
                        		
                        		logMessage.setLogLevel(level);
                        		logMessage.setLogMessage(line);
                        		logMessage.setTimestamp(new Date().getTime());
                        		logManager.log(logMessage, 
    	                        		securityConfig.getManagerUser(), 
    	                        		GridAppsDConstants.topic_simulationLog+simulationId);
                        	}
                        }
                    } catch (IOException e) {
                    	if(!(e.getMessage().contains("Stream closed")))
                    		logMessage.setLogLevel(LogLevel.ERROR);
                    		logMessage.setProcessStatus(ProcessStatus.ERROR);
                    		logMessage.setLogMessage("Error reading input stream of simulator process: "+e.getMessage());
                    		logMessage.setTimestamp(new Date().getTime());
                    		logManager.log(logMessage, 
    	                    		securityConfig.getManagerUser(), 
    	                    		GridAppsDConstants.topic_simulationLog+simulationId);
                    }

                }catch(IOException e){
                	e.printStackTrace();
                }
                            }
        }.start();
    }


    class GossFncsResponseEvent implements GossResponseEvent{
        InitializedTracker initializedTracker;
        SimulationTracker simulationTracker;
        LogManager logManager;
        int simulationId;
        DataResponse dataResponse;
        LogMessage logMessage;
        Gson gson;
        FncsBridgeResponse responseJson;
        public GossFncsResponseEvent(LogManager logManager, InitializedTracker initialized, SimulationTracker simFinished, int id) {
            this.logManager = logManager;
            initializedTracker = initialized;
            simulationTracker = simFinished;
            simulationId = id;
            logMessage = new LogMessage(this.getClass().getSimpleName(), 
            		Integer.toString(simulationId),
            		new Date().getTime(),
            		null,
            		LogLevel.DEBUG,
            		ProcessStatus.RUNNING,
            		true);
        }


        @Override
        public void onMessage(Serializable response) {
            try{
                //Parse response
                // if it is an isInitialized response, check the value and send timesteps if true, or wait and publish another check if false

                dataResponse = (DataResponse)response;
                
                logMessage.setTimestamp(new Date().getTime());
                logMessage.setLogMessage("FNCS-GOSS Bridge response:"+dataResponse.getData());
                logManager.log(logMessage,simContext.getSimulationUser(),
                        GridAppsDConstants.topic_platformLog);

                gson = new Gson();

                responseJson = gson.fromJson(dataResponse.getData().toString(), FncsBridgeResponse.class);
                //log.debug("FNCS output message: "+responseJson);
                if("isInitialized".equals(responseJson.command)){
                    log.debug("FNCS Initialized response: "+responseJson);
                    if("True".equals(responseJson.response)){
                        //log.info("FNCS is initialized "+initializedTracker);
                        initializedTracker.isInited = true;
                    }
                } else if("simulationFinished".equals(responseJson.command)) {
                    //log.debug("FNCS simulation finished");
                    simulationTracker.isFinished = true;
                }

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
