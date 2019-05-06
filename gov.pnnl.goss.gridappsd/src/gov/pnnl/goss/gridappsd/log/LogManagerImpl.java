/*******************************************************************************
 * Copyright (c) 2017, Battelle Memorial Institute All rights reserved.
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

package gov.pnnl.goss.gridappsd.log;

import gov.pnnl.goss.gridappsd.api.LogDataManager;
import gov.pnnl.goss.gridappsd.api.LogManager;
import gov.pnnl.goss.gridappsd.dto.LogMessage;
import gov.pnnl.goss.gridappsd.dto.LogMessage.LogLevel;
import gov.pnnl.goss.gridappsd.dto.LogMessage.ProcessStatus;
import gov.pnnl.goss.gridappsd.dto.RequestLogMessage;
import gov.pnnl.goss.gridappsd.utils.GridAppsDConstants;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Date;

import org.apache.felix.dm.annotation.api.Component;
import org.apache.felix.dm.annotation.api.ServiceDependency;
import org.apache.felix.dm.annotation.api.Start;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pnnl.goss.core.Client;
import pnnl.goss.core.Client.PROTOCOL;
import pnnl.goss.core.ClientFactory;
import pnnl.goss.core.DataResponse;
import pnnl.goss.core.GossResponseEvent;

/**
 * This class implements functionalities for Internal Function 409 Log Manager.
 * LogManager is responsible for logging messages coming from platform and other
 * processes in log file/stream as well as data store.
 * 
 * @author shar064
 *
 */
@Component
public class LogManagerImpl implements LogManager {

	private static Logger log = LoggerFactory.getLogger(LogManagerImpl.class);

	@ServiceDependency
	private volatile LogDataManager logDataManager;

	@ServiceDependency
	ClientFactory clientFactory;

	Client client;

	public LogManagerImpl() {
	}

	public LogManagerImpl(LogDataManager logDataManager) {
		this.logDataManager = logDataManager;
	}

	@Start
	private void start() {
		LogMessage logMessage = new LogMessage();
		try {
			UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
					GridAppsDConstants.username, GridAppsDConstants.password);
			client = clientFactory.create(PROTOCOL.STOMP, credentials);
			logMessage.setLogLevel(LogLevel.DEBUG);
			logMessage.setSource(this.getClass().getName());
			logMessage.setProcessStatus(ProcessStatus.RUNNING);
			logMessage.setStoreToDb(true);
			logMessage.setLogMessage("Starting " + this.getClass().getName());
			client.publish(GridAppsDConstants.topic_platformLog, logMessage);
			
			client.subscribe(GridAppsDConstants.topic_simulationLog+".>", new GossResponseEvent() {
				
				@Override
				public void onMessage(Serializable message) {
					
					DataResponse event = (DataResponse)message;
					
					try{
						LogMessage logMessage = LogMessage.parse(event.getData().toString());
						log(logMessage, event.getUsername(), null);
						
					}
					catch(Exception e){
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						e.printStackTrace(pw);
						this.error(sw.toString(),event.getUsername(),null);
					}
				}
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void log(LogMessage message, String username, String topic) {
		
		String source = message.getSource();
		String processId = message.getProcessId();
		long timestamp = new Date().getTime();
		String log_message = message.getLogMessage();
		//Default log message to empty if it is null to prevent sql error
		if(log_message==null)
			log_message = "";
		LogLevel logLevel = message.getLogLevel();
		ProcessStatus status = message.getProcessStatus();
		Boolean storeToDb = message.getStoreToDb();
		
		String logString;
		if(processId!=null)
			logString = String.format("%s|%s|%s|%s|%s|%s\n%s\n", timestamp, source, processId,
				status, username, logLevel, log_message);
		else
			logString = String.format("%s|%s|%s|%s|%s\n%s\n", timestamp, source,
					status, username, logLevel, log_message);
		if(logString.length() > 200 && message.getLogLevel()!=LogLevel.ERROR) {
			logString = logString.substring(0,200);
		}
		
		log(logString, processId, status, logLevel, source, timestamp, storeToDb, username, topic);
		
	}
	
	private void log(String logString, String processId, ProcessStatus status, LogLevel logLevel, String source, long timestamp, boolean storeToDb, String username, String topic){
		
		if (topic != null && client != null)
			client.publish(topic, new LogMessage(source, processId, logString, logLevel, status).toString());
		
		switch(logLevel) {
			case TRACE:	log.trace(logString);
						break;
			case DEBUG:	log.debug(logString);
						break;
			case INFO:	log.info(logString);
						break;
			case WARN:	log.warn(logString);
						break;		
			case ERROR:	log.error(logString);
						break;
			case FATAL:	log.error(logString);
						break;
			default:	log.debug(logString);
						break;
		}
		
		if(storeToDb)
			store(source, processId,timestamp,logString,logLevel,status,username);

	}
	
	public void log(LogMessage message, String topic) {
		this.log(message, GridAppsDConstants.username, topic);
	}
	
	public void debug(String logString, String processId, ProcessStatus status, String topic){
		
		LogMessage logMessage = new LogMessage();
		logMessage.setLogLevel(LogLevel.DEBUG);
		logMessage.setProcessId(processId);
		logMessage.setProcessStatus(status);
		logMessage.setSource(Thread.currentThread().getStackTrace()[2].getClassName());
		
		this.log(logMessage, topic);
		client.publish(topic, logMessage);
		
	}
	
	public void info(String logString, String processId, ProcessStatus status, String topic){
		
		LogMessage logMessage = new LogMessage();
		logMessage.setLogLevel(LogLevel.INFO);
		logMessage.setProcessId(processId);
		logMessage.setProcessStatus(status);
		logMessage.setSource(Thread.currentThread().getStackTrace()[2].getClassName());
		
		this.log(logMessage, topic);
		client.publish(topic, logMessage);
		
	}
	
	public void trace(String logString, String processId, ProcessStatus status, String topic){
		
		LogMessage logMessage = new LogMessage();
		logMessage.setLogLevel(LogLevel.TRACE);
		logMessage.setProcessId(processId);
		logMessage.setProcessStatus(status);
		logMessage.setSource(Thread.currentThread().getStackTrace()[2].getClassName());
		
		this.log(logMessage, topic);
		client.publish(topic, logMessage);
		
	}
	
	public void error(String logString, String processId, ProcessStatus status, String topic){
		LogMessage logMessage = new LogMessage();
		logMessage.setLogLevel(LogLevel.ERROR);
		logMessage.setProcessId(processId);
		logMessage.setProcessStatus(status);
		logMessage.setSource(Thread.currentThread().getStackTrace()[2].getClassName());
		this.log(logMessage, topic);
	}
	
	public void fatal(String logString, String processId, ProcessStatus status, String topic){
		LogMessage logMessage = new LogMessage();
		logMessage.setLogLevel(LogLevel.ERROR);
		logMessage.setProcessId(processId);
		logMessage.setProcessStatus(status);
		logMessage.setSource(Thread.currentThread().getStackTrace()[2].getClassName());
		this.log(logMessage, topic);
	}

	public void warn(String logString, String processId, ProcessStatus status, String topic){
		LogMessage logMessage = new LogMessage();
		logMessage.setLogLevel(LogLevel.ERROR);
		logMessage.setProcessId(processId);
		logMessage.setProcessStatus(status);
		logMessage.setSource(Thread.currentThread().getStackTrace()[2].getClassName());
		this.log(logMessage, topic);
	}
	
	
	
	private void store(String source, String requestId, long timestamp,
			String log_message, LogLevel log_level, ProcessStatus process_status, String username) {
		
		//TODO: Save log in data store using DataManager
		logDataManager.store(source, requestId, timestamp,
				log_message, log_level, process_status, username);
		log.debug("log saved");

	}

	/**
	 * Calls LogDataManager to query log messages that matches the keys in
	 * LogMessage objects.
	 * 
	 * @param message
	 *            an Object of gov.pnnl.goss.gridappsd.dto.LogMessage
	 */
	@Override
	public void get(RequestLogMessage message, String resultTopic, String logTopic) {
		
		if(message.getQuery()==null){
			String source = message.getSource();
			String requestId = message.getProcessId();
			LogLevel log_level = message.getLogLevel();
			ProcessStatus process_status = message.getProcessStatus();
			String username = "system";
			logDataManager.query(source, requestId, message.getStartTime(),message.getEndTime(),log_level, process_status, username);
		}
		else{
			logDataManager.query(message.getQuery());
		}
		
		
	}

	@Override
	public LogDataManager getLogDataManager() {
		return this.logDataManager;
	}

}
