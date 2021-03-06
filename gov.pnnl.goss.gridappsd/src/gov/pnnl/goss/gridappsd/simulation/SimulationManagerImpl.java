/*******************************************************************************
 * Copyright 2017, Battelle Memorial Institute All rights reserved.
 * Battelle Memorial Institute (hereinafter Battelle) hereby grants permission to any person or entity
 * lawfully obtaining a copy of this software and associated documentation files (hereinafter the
 * Software) to redistribute and use the Software in source and binary forms, with or without modification.
 * Such person or entity may use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and may permit others to do so, subject to the following conditions:
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the
 * following disclaimers.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Other than as used herein, neither the name Battelle Memorial Institute or Battelle may be used in any
 * form whatsoever without the express written consent of Battelle.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * BATTELLE OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * General disclaimer for use with OSS licenses
 *
 * This material was prepared as an account of work sponsored by an agency of the United States Government.
 * Neither the United States Government nor the United States Department of Energy, nor Battelle, nor any
 * of their employees, nor any jurisdiction or organization that has cooperated in the development of these
 * materials, makes any warranty, express or implied, or assumes any legal liability or responsibility for
 * the accuracy, completeness, or usefulness or any information, apparatus, product, software, or process
 * disclosed, or represents that its use would not infringe privately owned rights.
 *
 * Reference herein to any specific commercial product, process, or service by trade name, trademark, manufacturer,
 * or otherwise does not necessarily constitute or imply its endorsement, recommendation, or favoring by the United
 * States Government or any agency thereof, or Battelle Memorial Institute. The views and opinions of authors expressed
 * herein do not necessarily state or reflect those of the United States Government or any agency thereof.
 *
 * PACIFIC NORTHWEST NATIONAL LABORATORY operated by BATTELLE for the
 * UNITED STATES DEPARTMENT OF ENERGY under Contract DE-AC05-76RL01830
 ******************************************************************************/
package gov.pnnl.goss.gridappsd.simulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;

import org.apache.felix.dm.annotation.api.Component;
import org.apache.felix.dm.annotation.api.ServiceDependency;
import org.apache.felix.dm.annotation.api.Start;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import gov.pnnl.goss.gridappsd.api.ConfigurationManager;
import gov.pnnl.goss.gridappsd.api.LogManager;
import gov.pnnl.goss.gridappsd.api.ServiceManager;
import gov.pnnl.goss.gridappsd.api.SimulationManager;
import gov.pnnl.goss.gridappsd.dto.FncsBridgeResponse;
import gov.pnnl.goss.gridappsd.dto.LogMessage;
import gov.pnnl.goss.gridappsd.dto.SimulationConfig;
import gov.pnnl.goss.gridappsd.dto.LogMessage.LogLevel;
import gov.pnnl.goss.gridappsd.dto.LogMessage.ProcessStatus;
import pnnl.goss.core.Client;
import pnnl.goss.core.Client.PROTOCOL;
import pnnl.goss.core.ClientFactory;
import pnnl.goss.core.GossResponseEvent;
import pnnl.goss.core.server.ServerControl;
import riotcmd.json;
import gov.pnnl.goss.gridappsd.utils.GridAppsDConstants;
import gov.pnnl.goss.gridappsd.utils.RunCommandLine;

/**
 * This represents Internal Function 405 Simulation Control Manager.
 * This is the management function that controls the running/execution of the Distribution Simulator (401).
 * @author shar064
 */

@Component
public class SimulationManagerImpl implements SimulationManager{

	private static Logger log = LoggerFactory.getLogger(SimulationManagerImpl.class);
	final static int MAX_INIT_ATTEMPTS = 50;

	Client client = null;

	@ServiceDependency
	private volatile ClientFactory clientFactory;

	@ServiceDependency
	ServerControl serverControl;

	@ServiceDependency
	private volatile ConfigurationManager configurationManager;

	@ServiceDependency
	private volatile ServiceManager serviceManager;
	
	@ServiceDependency
	LogManager logManager;

	public SimulationManagerImpl(){ }


	public SimulationManagerImpl(ClientFactory clientFactory, ServerControl serverControl,
			LogManager logManager, ConfigurationManager configurationManager) {
		this.clientFactory = clientFactory;
		this.serverControl = serverControl;
		this.logManager = logManager;
		this.configurationManager = configurationManager;
	}
	@Start
	public void start() throws Exception{
		
		Credentials credentials = new UsernamePasswordCredentials(
				GridAppsDConstants.username, GridAppsDConstants.password);
		client = clientFactory.create(PROTOCOL.STOMP,credentials);
		client.publish("goss.gridappsd.log.platform", new LogMessage(this.getClass().getName(), 
				new Date().getTime(), 
				this.getClass().getName()+" Started", 
				LogLevel.INFO, 
				ProcessStatus.STARTED, 
				true).toString());
		
		
		

	}

	/**
	 * This method is called by Process Manager to start a simulation
	 * @param simulationId
	 * @param simulationFile
	 */
	@Override
	public void startSimulation(int simulationId, File simulationFile, SimulationConfig simulationConfig){

			try {
				logManager.log(new LogMessage(this.getClass().getName()+"-"+Integer.toString(simulationId), 
						new Date().getTime(), 
						"Starting simulation "+simulationId, 
						LogLevel.INFO, 
						ProcessStatus.STARTING, 
						true),GridAppsDConstants.username);
			} catch (Exception e2) {
				log.warn("Error while reporting status "+e2.getMessage());
			}


			Thread thread = new Thread(new Runnable() {

				@Override
				public void run() {

					Process gridlabdProcess = null;
					Process fncsProcess = null;
					Process fncsBridgeProcess = null;
					Process vvoAppProcess = null;
					InitializedTracker isInitialized = new InitializedTracker();
					try{

						File defaultLogDir = simulationFile.getParentFile();

						//Start FNCS
						//TODO, verify no errors on this
						String broker_location = "tcp://*:5570";
						if(simulationConfig!=null && simulationConfig.model_creation_config!=null && simulationConfig.model_creation_config.schedule_name!=null && simulationConfig.model_creation_config.schedule_name.trim().length()>0){
							broker_location = "tcp://"+simulationConfig.getSimulation_broker_location()+":"+String.valueOf(simulationConfig.getSimulation_broker_port());
							File serviceDir = serviceManager.getServiceConfigDirectory();
							//copy zipload_schedule.player file
							try{
								RunCommandLine.runCommand("cp "+serviceDir.getAbsolutePath()+File.separator+"etc"+File.separator+"zipload_schedule.player "+simulationFile.getParentFile().getAbsolutePath()+File.separator+simulationConfig.model_creation_config.schedule_name+".player");
							}catch(Exception e){
								log.warn("Could not copy player file to working directory");
							}
						}
						
						logManager.log(new LogMessage(this.getClass().getName()+"-"+Integer.toString(simulationId), 
								new Date().getTime(), 
								"Calling "+getPath(GridAppsDConstants.FNCS_PATH)+" 2",
								LogLevel.INFO, 
								ProcessStatus.STARTING, 
								true),GridAppsDConstants.username);
						
						ProcessBuilder fncsBuilder = new ProcessBuilder(getPath(GridAppsDConstants.FNCS_PATH), "2");
						fncsBuilder.redirectErrorStream(true);
						fncsBuilder.redirectOutput(new File(defaultLogDir.getAbsolutePath()+File.separator+"fncs.log"));
						Map<String, String> fncsEnvironment = fncsBuilder.environment();
						fncsEnvironment.put("FNCS_BROKER", broker_location);
						fncsProcess = fncsBuilder.start();
						// Watch the process
						watch(fncsProcess, "FNCS");
						//TODO: check if FNCS is started correctly and send publish simulation status accordingly

						
						logManager.log(new LogMessage(this.getClass().getName()+"-"+Integer.toString(simulationId), 
								new Date().getTime(), 
								"FNCS Co-Simulator started", 
								LogLevel.INFO, 
								ProcessStatus.RUNNING, 
								true),GridAppsDConstants.username);


						//client.publish(GridAppsDConstants.topic_simulationStatus+simulationId, "FNCS Co-Simulator started");

						//Start GridLAB-D
						logManager.log(new LogMessage(this.getClass().getName()+"-"+Integer.toString(simulationId), 
								new Date().getTime(), 
								"Calling "+getPath(GridAppsDConstants.GRIDLABD_PATH)+" "+simulationFile,
								LogLevel.INFO, 
								ProcessStatus.RUNNING, 
								true),GridAppsDConstants.username);
						ProcessBuilder gridlabDBuilder = new ProcessBuilder(getPath(GridAppsDConstants.GRIDLABD_PATH), simulationFile.getAbsolutePath());
						gridlabDBuilder.redirectErrorStream(true);
						gridlabDBuilder.redirectOutput(new File(defaultLogDir.getAbsolutePath()+File.separator+"gridlabd.log"));
						//launch from directory containing simulation files
						gridlabDBuilder.directory(simulationFile.getParentFile());
						gridlabdProcess = gridlabDBuilder.start();
						// Watch the process
						watch(gridlabdProcess, "GridLABD");


						//TODO: check if GridLAB-D is started correctly and send publish simulation status accordingly

						logManager.log(new LogMessage(this.getClass().getName()+"-"+Integer.toString(simulationId), 
								new Date().getTime(), 
								"GridLAB-D started", 
								LogLevel.INFO, 
								ProcessStatus.RUNNING, 
								true),GridAppsDConstants.username);
						
												
						//Start VVO Application
						//TODO filname really should be constant
						String vvoInputFile = simulationFile.getParentFile().getAbsolutePath()+File.separator+"vvo_inputs.json";
						logManager.log(new LogMessage(this.getClass().getName()+"-"+Integer.toString(simulationId), 
								new Date().getTime(), 
								"Calling "+"python "+getPath(GridAppsDConstants.VVO_APP_PATH)+" "+simulationId+" "+vvoInputFile,
								LogLevel.INFO, 
								ProcessStatus.RUNNING, 
								true),GridAppsDConstants.username);
						ProcessBuilder vvoAppBuilder = new ProcessBuilder("python", getPath(GridAppsDConstants.VVO_APP_PATH), "-f",vvoInputFile, ""+simulationId);
						vvoAppBuilder.redirectErrorStream(true);
						vvoAppBuilder.redirectOutput(new File(defaultLogDir.getAbsolutePath()+File.separator+"vvo_app.log"));
						vvoAppProcess = vvoAppBuilder.start();
						// Watch the process
						watch(vvoAppProcess, "VVO Application");

						logManager.log(new LogMessage(this.getClass().getName()+"-"+Integer.toString(simulationId),
								new Date().getTime(), 
								"FNCS-GOSS Bridge started", 
								LogLevel.INFO, 
								ProcessStatus.RUNNING, 
								true),GridAppsDConstants.username);

						//Start GOSS-FNCS Bridge
						logManager.log(new LogMessage(this.getClass().getName()+"-"+Integer.toString(simulationId),
								new Date().getTime(), 
								"Calling "+"python "+getPath(GridAppsDConstants.FNCS_BRIDGE_PATH)+" "+simulationConfig.getSimulation_name(),
								LogLevel.INFO, 
								ProcessStatus.RUNNING, 
								true),GridAppsDConstants.username);
						
						ProcessBuilder fncsBridgeBuilder = new ProcessBuilder("python", getPath(GridAppsDConstants.FNCS_BRIDGE_PATH), simulationConfig.getSimulation_name(), broker_location);
						fncsBridgeBuilder.redirectErrorStream(true);
						fncsBridgeBuilder.redirectOutput(new File(defaultLogDir.getAbsolutePath()+File.separator+"fncs_goss_bridge.log"));
						fncsBridgeProcess = fncsBridgeBuilder.start();
						// Watch the process
						watch(fncsBridgeProcess, "FNCS GOSS Bridge");

						//TODO: check if bridge is started correctly and send publish simulation status accordingly

						logManager.log(new LogMessage(this.getClass().getName()+"-"+Integer.toString(simulationId),
								new Date().getTime(), 
								"FNCS-GOSS Bridge started", 
								LogLevel.INFO, 
								ProcessStatus.RUNNING, 
								true),GridAppsDConstants.username);


						//Subscribe to fncs-goss-bridge output topic
						client.subscribe(GridAppsDConstants.topic_FNCS_output, new GossFncsResponseEvent(logManager, isInitialized, simulationId));

						int initAttempts = 0;
						while(!isInitialized.isInited && initAttempts<MAX_INIT_ATTEMPTS){
							//Send 'isInitialized' call to fncs-goss-bridge to check initialization until it is initialized.
							//TODO add limiting how long it checks for initialized, or cancel if the fncs process exits
							//This call would return true/false for initialization and simulation output of time step 0.
							logManager.log(new LogMessage(this.getClass().getName()+"-"+Integer.toString(simulationId),
									new Date().getTime(), 
									"Checking fncs is initialized, currently "+isInitialized.isInited,
									LogLevel.INFO, 
									ProcessStatus.RUNNING, 
									true),GridAppsDConstants.username);
							
							client.publish(GridAppsDConstants.topic_FNCS_input, "{\"command\": \"isInitialized\"}");
							initAttempts++;
							Thread.sleep(1000);

						}

						if(initAttempts<MAX_INIT_ATTEMPTS){
							logManager.log(new LogMessage(Integer.toString(simulationId), 
									new Date().getTime(), 
									"FNCS Initialized", 
									LogLevel.INFO, 
									ProcessStatus.RUNNING, 
									true),GridAppsDConstants.username);


							//Send the timesteps by second for the amount of time specified in the simulation config
	                        sendTimesteps(simulationConfig, simulationId);
						} else {
							logManager.log(new LogMessage(Integer.toString(simulationId), 
									new Date().getTime(), 
									"FNCS Initialization Failed", 
									LogLevel.ERROR, 
									ProcessStatus.ERROR,  
									true),GridAppsDConstants.username);

						}

                        //call to stop the fncs broker
					    client.publish(GridAppsDConstants.topic_FNCS_input, "{\"command\":  \"stop\"}");
					    logManager.log(new LogMessage(Integer.toString(simulationId), 
								new Date().getTime(), 
								"Simulation "+simulationId+" complete", 
								LogLevel.INFO, 
								ProcessStatus.COMPLETE,
								true),GridAppsDConstants.username);
					}
					catch(Exception e){
							log.error("Error during simulation",e);
							try {
								logManager.log(new LogMessage(Integer.toString(simulationId), 
										new Date().getTime(), 
										"Simulation error: "+e.getMessage(),
										LogLevel.ERROR, 
										ProcessStatus.ERROR,
										true),GridAppsDConstants.username);
							} catch (Exception e1) {
								log.error("Error while reporting error status", e);
							}
					} finally {
						//shut down fncs broker and gridlabd and bridge if still running
						if(fncsProcess!=null){
							fncsProcess.destroy();
						}
						if(gridlabdProcess!=null){
							gridlabdProcess.destroy();
						}
						if(fncsBridgeProcess!=null){
							fncsBridgeProcess.destroy();
						}
					}
				}
			});

			thread.start();
	}


    class InitializedTracker {
    	public boolean isInited = false;
    }


    class GossFncsResponseEvent implements GossResponseEvent{
		InitializedTracker initializedTracker;
		LogManager logManager;
		int simulationId;
		public GossFncsResponseEvent(LogManager logManager, InitializedTracker initialized, int id) {
			this.logManager = logManager;
			initializedTracker = initialized;
			simulationId = id;
		}


		@Override
		public void onMessage(Serializable response) {
			try{
				//Parse response
				// if it is an isInitialized response, check the value and send timesteps if true, or wait and publish another check if false
				logManager.log(new LogMessage(Integer.toString(simulationId), 
						new Date().getTime(), 
						 "FNCS-GOSS Bridge response:"+response, 
							LogLevel.INFO, 
							ProcessStatus.RUNNING,
						true),GridAppsDConstants.username);

				Gson  gson = new Gson();
				FncsBridgeResponse responseJson = gson.fromJson(response.toString(), FncsBridgeResponse.class);
				log.debug("FNCS output message: "+responseJson);
				if("isInitialized".equals(responseJson.command)){
					log.debug("FNCS Initialized response: "+responseJson);
					if("True".equals(responseJson.response)){
                        log.info("FNCS is initialized "+initializedTracker);
						initializedTracker.isInited = true;
					}
				} else {
					//System.out.println("RESPONSE COMMAND "+responseJson.command);
					//??
				}



			}catch (Exception e){
				e.printStackTrace();
			}
		}
	}


	private void sendTimesteps(SimulationConfig simulationConfig, int simulationId) throws Exception{
		// Send fncs timestep updates for the specified duration.

		String startTimeStr = simulationConfig.getStart_time();
		Date startTime = GridAppsDConstants.SDF_GLM_CLOCK.parse(startTimeStr);
		long endTime = startTime.getTime() + (simulationConfig.getDuration()*1000);
		long currentTime = startTime.getTime(); //incrementing integer 0 ,1, 2.. representing seconds
		int seconds = 0;
		while(currentTime < endTime){
			//send next timestep to fncs bridge 
			logManager.log(new LogMessage(Integer.toString(simulationId), 
					new Date().getTime(), 
					"Sending timestep "+seconds, 
					LogLevel.INFO, 
					ProcessStatus.RUNNING, 
					true),GridAppsDConstants.username);
			String message = "{\"command\": \"nextTimeStep\", \"currentTime\": "+seconds+"}";
			client.publish(GridAppsDConstants.topic_FNCS_input, message);
			Thread.sleep(simulationConfig.timestep_frequency);

			seconds++;
			currentTime += simulationConfig.timestep_increment;
		}
	}


	private String getPath(String key){
		String path = configurationManager.getConfigurationProperty(key);
		if(path==null){
			log.warn("Configuration property not found, defaulting to .: "+key);
			path = ".";
		}
		return path;
	}



	private void watch(final Process process, String processName) {
	    new Thread() {
	        public void run() {
	            BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
	            String line = null;
	            try {
	                while ((line = input.readLine()) != null) {
	                    log.info(processName+": "+line);
	                }
	            } catch (IOException e) {
	                log.error("Error on process "+processName, e);
	            }
	        }
	    }.start();
	}
}
