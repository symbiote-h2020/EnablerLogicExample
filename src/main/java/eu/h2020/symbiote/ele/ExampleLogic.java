package eu.h2020.symbiote.ele;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.CloudResource;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.ele.model.MessageRequest;
import eu.h2020.symbiote.ele.model.MessageResponse;
import eu.h2020.symbiote.enabler.messaging.model.ActuatorExecutionTaskInfo;
import eu.h2020.symbiote.enabler.messaging.model.EnablerLogicDataAppearedMessage;
import eu.h2020.symbiote.enabler.messaging.model.NotEnoughResourcesAvailable;
import eu.h2020.symbiote.enabler.messaging.model.PlatformProxyResourceInfo;
import eu.h2020.symbiote.enabler.messaging.model.ResourceManagerAcquisitionStartResponse;
import eu.h2020.symbiote.enabler.messaging.model.ResourceManagerTaskInfoRequest;
import eu.h2020.symbiote.enabler.messaging.model.ResourceManagerTaskInfoResponse;
import eu.h2020.symbiote.enabler.messaging.model.ResourceManagerTasksStatus;
import eu.h2020.symbiote.enabler.messaging.model.ResourcesUpdated;
import eu.h2020.symbiote.enabler.messaging.model.ServiceExecutionTaskInfo;
import eu.h2020.symbiote.enabler.messaging.model.ServiceParameter;
import eu.h2020.symbiote.enablerlogic.EnablerLogic;
import eu.h2020.symbiote.enablerlogic.ProcessingLogic;
import eu.h2020.symbiote.enablerlogic.messaging.RegistrationHandlerClientService;
import eu.h2020.symbiote.enablerlogic.messaging.properties.EnablerLogicProperties;
import eu.h2020.symbiote.model.cim.Actuator;
import eu.h2020.symbiote.model.cim.FeatureOfInterest;
import eu.h2020.symbiote.model.cim.LengthRestriction;
import eu.h2020.symbiote.model.cim.Location;
import eu.h2020.symbiote.model.cim.Observation;
import eu.h2020.symbiote.model.cim.ObservationValue;
import eu.h2020.symbiote.model.cim.PrimitiveDatatype;
import eu.h2020.symbiote.model.cim.Property;
import eu.h2020.symbiote.model.cim.Service;
import eu.h2020.symbiote.model.cim.StationarySensor;
import eu.h2020.symbiote.model.cim.UnitOfMeasurement;
import eu.h2020.symbiote.model.cim.WGS84Location;
import eu.h2020.symbiote.rapplugin.domain.Capability;
import eu.h2020.symbiote.rapplugin.domain.Parameter;
import eu.h2020.symbiote.rapplugin.messaging.rap.ActuatingResourceListener;
import eu.h2020.symbiote.rapplugin.messaging.rap.InvokingServiceListener;
import eu.h2020.symbiote.rapplugin.messaging.rap.RapPlugin;
import eu.h2020.symbiote.rapplugin.messaging.rap.RapPluginException;
import eu.h2020.symbiote.rapplugin.messaging.rap.ReadingResourceListener;
import eu.h2020.symbiote.security.accesspolicies.common.AccessPolicyType;
import eu.h2020.symbiote.security.accesspolicies.common.singletoken.SingleTokenAccessPolicySpecifier;
import eu.h2020.symbiote.security.commons.exceptions.custom.InvalidArgumentsException;

@Component
public class ExampleLogic implements ProcessingLogic {
    private static final Logger LOG = LoggerFactory.getLogger(ExampleLogic.class);
    
    private EnablerLogic enablerLogic;
    
    @Autowired
    private EnablerLogicProperties props;
    
    @Autowired
    private RegistrationHandlerClientService rhClientService;
    
    @Autowired
    private RapPlugin rapPlugin;

    @Value("${platform.id}")
    private String myPlatformId;

    @Override
    public void initialization(EnablerLogic enablerLogic) {
        this.enablerLogic = enablerLogic;

        registerResources();
        
        registerRapConsumers();
        
        //asyncCommunication();
        //syncCommunication();

        queryParisTemperature();
    }

    private void registerRapConsumers() {
        rapPlugin.registerReadingResourceListener(new ReadingResourceListener() {
            
            @Override
            public List<Observation> readResourceHistory(String resourceId) {
                if("el_isen1".equals(resourceId))
                    return new ArrayList<>(Arrays.asList(createObservation(resourceId), createObservation(resourceId)));

                return null;
            }
            
            @Override
            public Observation readResource(String resourceId) {
                if("el_isen1".equals(resourceId)) {
                   return createObservation(resourceId);
                }
                    
                return null;
            }
        });
        
        rapPlugin.registerActuatingResourceListener(new ActuatingResourceListener() {
			
			@Override
			public void actuateResource(String resourceId, Map<String,Capability> parameters) {
                LOG.debug("writing to resource {} body:{}", resourceId, parameters);
                try {
	                if("el_iaid1".equals(resourceId)) {
	                    Capability lightCapability = parameters.get("OnOffCapability");
	                    Assert.notNull(lightCapability, "Capability 'on' is required.");
	                    Parameter parameter = lightCapability.getParameters().get("on");
	                    Assert.notNull(parameter, "Parameter 'on' in capability 'OnOffCapability' is required.");
	                    Object objectValue = parameter.getValue();
	                    Assert.isInstanceOf(Boolean.class, objectValue, "Parameter 'on' of capability 'OnOffCapability' should be boolean.");
	                    if((Boolean) objectValue) {
	                        LOG.debug("*** Turning on AC {}", resourceId);
	                    } else {
	                        LOG.debug("*** Turning off AC {}", resourceId);
	                    }
	                } else {
	                	throw new RapPluginException(HttpStatus.NOT_FOUND.value(), "Actuator not found");
	                }
                } catch (IllegalArgumentException e) {
                	throw new RapPluginException(HttpStatus.BAD_REQUEST.value(), e.getMessage());
                }
            }
        });
        
		rapPlugin.registerInvokingServiceListener(new InvokingServiceListener() {
			
			@Override
			public Object invokeService(String resourceId, Map<String,Parameter> parameters) {
		        LOG.debug("invoking service {} parameters:{}", resourceId, parameters);
		        
		        try {
		            if("el_isrid1".equals(resourceId)) {
		            	Parameter parameter = parameters.get("humidityTarget");
		                Assert.notNull(parameter, "Capability 'humidityTarget' is required.");
		                Object objectValue = parameter.getValue();
		                Assert.isInstanceOf(String.class, objectValue, "Parameter 'humidityTarget' should be string of length form 2-10.");
		                String value = (String) objectValue;
		                LOG.debug("Invoking service {} with param {}.", resourceId, value);
		                LOG.info("*** Humidity service target is {}", value);
		                return "ok";
		            } else {
		            	throw new RapPluginException(HttpStatus.NOT_FOUND.value(), "Service not found");
		            }
		        } catch (IllegalArgumentException e) {
		        	throw new RapPluginException(HttpStatus.BAD_REQUEST.value(), e.getMessage());
		        }
			}
		});
    }
    
    public Observation createObservation(String sensorId) {        
        Location loc = createLocation();
        
        TimeZone zoneUTC = TimeZone.getTimeZone("UTC");
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        dateFormat.setTimeZone(zoneUTC);
        Date date = new Date();
        String timestamp = dateFormat.format(date);
        
        long ms = date.getTime() - 1000;
        date.setTime(ms);
        String samplet = dateFormat.format(date);
        
        ArrayList<ObservationValue> obsList = new ArrayList<>();
        ObservationValue obsval = 
                new ObservationValue(
                        Integer.toString(new Random().nextInt(50) - 10), // random temp. 
                        new Property("Temperature", "temp_iri", Arrays.asList("Air temperature")), 
                        new UnitOfMeasurement("C", "degree Celsius", "celsius_iri", Arrays.asList("")));
        obsList.add(obsval);
        
        obsval = new ObservationValue(
        		Integer.toString(new Random().nextInt(100)), // random humidity 
                new Property("Humidity", "humidity_iri", Arrays.asList("Air humidity")), 
                new UnitOfMeasurement("%", "percent", "humidity_iri", Arrays.asList("")));
        obsList.add(obsval);
        
        Observation obs = new Observation(sensorId, loc, timestamp, samplet , obsList);
        
        try {
            LOG.debug("Observation: \n{}", new ObjectMapper().writeValueAsString(obs));
        } catch (JsonProcessingException e) {
            LOG.error("Can not convert observation to JSON", e);
        }
        
        return obs;
    }


    private void registerResources() {
        List<CloudResource> cloudResources = new LinkedList<>();
        cloudResources.add(createSensorResource("el_isen1"));
        cloudResources.add(createActuatorResource("el_iaid1"));
        cloudResources.add(createServiceResource("el_isrid1"));

        // waiting for registrationHandler to create exchange
        int i = 1;
        while(i < 10) {
            try {
                LOG.debug("Attempting to register resources count {}.", i);
                rhClientService.registerResources(cloudResources);
                LOG.debug("Resources registered");
                break;
            } catch (Exception e) {
                i++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                }
            }
        }
    }

    private CloudResource createSensorResource(String internalId) {
        CloudResource cloudResource = new CloudResource();
        cloudResource.setInternalId(internalId);
        cloudResource.setPluginId(props.getEnablerName());

        try {
			cloudResource.setAccessPolicy(new SingleTokenAccessPolicySpecifier(AccessPolicyType.PUBLIC, null));
			cloudResource.setFilteringPolicy(new SingleTokenAccessPolicySpecifier(AccessPolicyType.PUBLIC, null));
		} catch (InvalidArgumentsException e) {
			e.printStackTrace();
		}
        
        StationarySensor sensor = new StationarySensor();
        cloudResource.setResource(sensor);
        sensor.setName("DefaultEnablerLogicSensor" + internalId);
        sensor.setDescription(Arrays.asList("Default sensor for testing EL"));

        FeatureOfInterest featureOfInterest = new FeatureOfInterest();
        sensor.setFeatureOfInterest(featureOfInterest);
        featureOfInterest.setName("outside air");
        featureOfInterest.setDescription(Arrays.asList("outside air quality"));
        featureOfInterest.setHasProperty(Arrays.asList("temperature,humidity".split(",")));
        
        sensor.setObservesProperty(Arrays.asList("temperature,humidity".split(",")));
        sensor.setLocatedAt(createLocation());
        sensor.setInterworkingServiceURL(props.getInterworkingInterfaceUrl());
        return cloudResource;        
    }

	private WGS84Location createLocation() {
		WGS84Location location = new WGS84Location(2.349014, 48.864716, 15, 
                "Paris", 
                Arrays.asList("This is Paris"));
		return location;
	}

    private CloudResource createActuatorResource(String internalId) {
        CloudResource cloudResource = new CloudResource();
        cloudResource.setInternalId(internalId);
        cloudResource.setPluginId(props.getEnablerName());
        
        try {
			cloudResource.setAccessPolicy(new SingleTokenAccessPolicySpecifier(AccessPolicyType.PUBLIC, null));
			cloudResource.setFilteringPolicy(new SingleTokenAccessPolicySpecifier(AccessPolicyType.PUBLIC, null));
		} catch (InvalidArgumentsException e) {
			e.printStackTrace();
		}
        
        Actuator actuator = new Actuator();
        cloudResource.setResource(actuator);
        
        actuator.setLocatedAt(createLocation());
        actuator.setName("Enabler_Logic_Example_Aircondition_1");
        actuator.setDescription(Arrays.asList("This is aircondition 1"));
        
        eu.h2020.symbiote.model.cim.Capability capability = new eu.h2020.symbiote.model.cim.Capability();
        actuator.setCapabilities(Arrays.asList(capability));
        
        capability.setName("OnOffCapabililty");

        // parameters
        eu.h2020.symbiote.model.cim.Parameter parameter = new eu.h2020.symbiote.model.cim.Parameter();
        capability.setParameters(Arrays.asList(parameter));
        parameter.setName("on");
        parameter.setMandatory(true);
        PrimitiveDatatype datatype = new PrimitiveDatatype();
		parameter.setDatatype(datatype);
		datatype.setBaseDatatype("boolean");
        
        actuator.setInterworkingServiceURL(props.getInterworkingInterfaceUrl());

        return cloudResource;
    }
    
    private CloudResource createServiceResource(String internalId) {
        CloudResource cloudResource = new CloudResource();
        cloudResource.setInternalId(internalId);
        cloudResource.setPluginId(props.getEnablerName());
        
        try {
			cloudResource.setAccessPolicy(new SingleTokenAccessPolicySpecifier(AccessPolicyType.PUBLIC, null));
			cloudResource.setFilteringPolicy(new SingleTokenAccessPolicySpecifier(AccessPolicyType.PUBLIC, null));
		} catch (InvalidArgumentsException e) {
			e.printStackTrace();
		}

        Service service = new Service();
        cloudResource.setResource(service);
        
        service.setName("Enabler_Logic_Example_Humidity_service_1");
        service.setDescription(Arrays.asList("This is humidity service 1 in Enabler Logic Example"));
        
        eu.h2020.symbiote.model.cim.Parameter parameter = new eu.h2020.symbiote.model.cim.Parameter();
        service.setParameters(Arrays.asList(parameter));

        parameter.setName("humidityTarget");
        parameter.setMandatory(true);
        // restriction
        LengthRestriction restriction = new LengthRestriction();
        restriction.setMin(2);
        restriction.setMax(10);
		parameter.setRestrictions(Arrays.asList(restriction));
		
		PrimitiveDatatype datatype = new PrimitiveDatatype();
		datatype.setArray(false);
		datatype.setBaseDatatype("http://www.w3.org/2001/XMLSchema#string");
		parameter.setDatatype(datatype);

        service.setInterworkingServiceURL(props.getInterworkingInterfaceUrl());

        return cloudResource;
    }

    private void asyncCommunication() {
        // asynchronous communications to another Enabler Logic component
        // register consumer for message type EnablerLogicDataAppearedMessage
        enablerLogic.registerAsyncMessageFromEnablerLogicConsumer(
            EnablerLogicDataAppearedMessage.class, 
            (m) -> LOG.info("Received from another EnablerLogic: {}", m));
        
        // send myself async message
        enablerLogic.sendAsyncMessageToEnablerLogic("EnablerLogicInterpolator", new EnablerLogicDataAppearedMessage());
    }
    
    private void syncCommunication() {
        // synchronous communication to another Enabler Logic component
        // register function for synchronous communication
        enablerLogic.registerSyncMessageFromEnablerLogicConsumer(
            MessageRequest.class, 
            (m) -> new MessageResponse("response: " + m.getRequest()));
        
        // send myself sync message
        MessageResponse response = enablerLogic.sendSyncMessageToEnablerLogic(
            "EnablerLogicInterpolator",
            new MessageRequest("request"),
            MessageResponse.class);
        
        LOG.info("Received sync response: {}", response.getResponse());
    }

    @Override
    public void measurementReceived(EnablerLogicDataAppearedMessage dataAppeared) {
        try {
            LOG.info("Received new Observations: {}", new ObjectMapper().writeValueAsString(dataAppeared));
        } catch (JsonProcessingException e) {
            LOG.error("Problem with deserializing EnablerLogicDataAppearedMessage", e);
        }
        
        if(dataAppeared.getObservations().isEmpty()) {
            LOG.warn("Received measurement data that is empty.");
            return;
        }
        
        temperatureLogic(dataAppeared);
        humidityLogic(dataAppeared);
    }

	private void humidityLogic(EnablerLogicDataAppearedMessage dataAppeared) {
		try {
			LOG.info("Logic for Paris humidity");
			dataAppeared.getObservations().get(0).getObsValues().stream()
				.filter(obsValue -> obsValue.getObsProperty().getName().equalsIgnoreCase("humidity"))
				.map(obsValue -> obsValue.getValue())
				.forEach(tempValue -> {
					int humidity = Integer.parseInt(tempValue);
					if(humidity > 60 || humidity < 30)
						turnOnHumidityService(40);
					else
						turnOffHumidityService();
				});
	    } catch (Exception e) {
	    	LOG.error("Error in logic for Paris temp", e);
	    }
	}
	
	private void turnOffHumidityService() {
		LOG.info("Turning OFF service");
		setHumidityServiceTarget("OFF");
	}
	
	private void turnOnHumidityService(int targetHumidity) {
		LOG.info("Setting humidiy service to target {}.", targetHumidity);
		setHumidityServiceTarget(String.valueOf(targetHumidity));
	}
	
	private void setHumidityServiceTarget(String target) {
		findHumidityService().ifPresent(resource -> {
	    	LOG.info("Setting service {} to target {}", resource.getResourceId(), target);
	    	enablerLogic.invokeService(new ServiceExecutionTaskInfo("humidityServiceTarget", 
	    			resource, props.getEnablerName(),  
	    			Arrays.asList(new ServiceParameter("humidityTarget", target))));
		});
	}
	
	private Optional<PlatformProxyResourceInfo> findHumidityService() {
		CoreQueryRequest coreQueryRequest = new CoreQueryRequest();
		coreQueryRequest.setName("Enabler_Logic_Example_Humidity_service_1");
		coreQueryRequest.setPlatform_id(myPlatformId); // this is only for this example
	
	    ResourceManagerTaskInfoRequest request = new ResourceManagerTaskInfoRequest(
	    		"humidityService", 1, 1, coreQueryRequest, 
	    		null, //"P0000-00-00T00:01:00",
	    		false, null, false, props.getEnablerName(), null);
	
	    ResourceManagerAcquisitionStartResponse response = enablerLogic.queryResourceManager(request);
	
	    try {
			LOG.info("Response JSON: {}", new ObjectMapper().writeValueAsString(response));
		} catch (JsonProcessingException e) {
			LOG.info("Response: {}", response);
		}
	    
	    if(response.getStatus() != ResourceManagerTasksStatus.SUCCESS) {
	    	LOG.warn("Did not found humidity service!!!");
	    	return Optional.empty();
	    }
	    	
	    ResourceManagerTaskInfoResponse resourceManagerTaskInfoResponse = response.getTasks().get(0);
		String resourceId = resourceManagerTaskInfoResponse.getResourceDescriptions().get(0).getId();
		String accessURL = resourceManagerTaskInfoResponse.getResourceUrls().get(resourceId);
		
		PlatformProxyResourceInfo info = new PlatformProxyResourceInfo();
		info.setAccessURL(accessURL);
		info.setResourceId(resourceId);
		return Optional.of(info);
	}

	private void temperatureLogic(EnablerLogicDataAppearedMessage dataAppeared) {
		try {
			LOG.info("Logic for Paris temp");
			dataAppeared.getObservations().get(0).getObsValues().stream()
				.filter(obsValue -> obsValue.getObsProperty().getName().equalsIgnoreCase("temperature"))
				.map(obsValue -> obsValue.getValue())
				.forEach(tempValue -> {
					if(Integer.parseInt(tempValue) > 25)
						actuateAirCondition(true);
					else
						actuateAirCondition(false);
				});
	    } catch (Exception e) {
	    	LOG.error("Error in logic for Paris temp", e);
	    }
	}
    
	private void actuateAirCondition(boolean state) {
		findAirConditionInfo().ifPresent(resource -> {
	    	LOG.info("Actuating {} with state {}", resource.getResourceId(), state);
	    	enablerLogic.triggerActuator(new ActuatorExecutionTaskInfo("triggerAirCondition", 
	    			resource, props.getEnablerName(), "OnOffCapability", 
	    			Arrays.asList(new ServiceParameter("on", state))));
		});
	}

	private Optional<PlatformProxyResourceInfo> findAirConditionInfo() {
		CoreQueryRequest coreQueryRequest = new CoreQueryRequest();
		coreQueryRequest.setName("Enabler_Logic_Example_Aircondition_1");
		coreQueryRequest.setPlatform_id(myPlatformId); // this is only for this example
	
	    ResourceManagerTaskInfoRequest request = new ResourceManagerTaskInfoRequest(
	    		"airCondition", 1, 1, coreQueryRequest, 
	    		null, //"P0000-00-00T00:01:00",
	    		false, null, false, props.getEnablerName(), null);
	
	    ResourceManagerAcquisitionStartResponse response = enablerLogic.queryResourceManager(request);
	
	    try {
			LOG.info("Response JSON: {}", new ObjectMapper().writeValueAsString(response));
		} catch (JsonProcessingException e) {
			LOG.info("Response: {}", response);
		}
	    
	    if(response.getStatus() != ResourceManagerTasksStatus.SUCCESS) {
	    	LOG.warn("Did not found aircondition actuator!!!");
	    	return Optional.empty();
	    }
	    	
	    ResourceManagerTaskInfoResponse resourceManagerTaskInfoResponse = response.getTasks().get(0);
		String resourceId = resourceManagerTaskInfoResponse.getResourceDescriptions().get(0).getId();
		String accessURL = resourceManagerTaskInfoResponse.getResourceUrls().get(resourceId);
		
		PlatformProxyResourceInfo info = new PlatformProxyResourceInfo();
		info.setAccessURL(accessURL);
		info.setResourceId(resourceId);
		return Optional.of(info);
	}

	private void queryParisTemperature() {
    	LOG.info("QUERY temperature in Paris");
        CoreQueryRequest coreQueryRequest = new CoreQueryRequest();
        coreQueryRequest.setLocation_long(2.349014);
        coreQueryRequest.setLocation_lat(48.864716);
        coreQueryRequest.setMax_distance(10_000); // radius 10km
        coreQueryRequest.setPlatform_id(myPlatformId); // this make sense only for testing
        coreQueryRequest.setObserved_property(Arrays.asList("temperature"));

        ResourceManagerTaskInfoRequest request = new ResourceManagerTaskInfoRequest(
        		"Paris-temp", 1, 1, coreQueryRequest, 
        		"P0000-00-00T00:01:00", // 1 min
        		false, null, true, props.getEnablerName(), null);

        ResourceManagerAcquisitionStartResponse response = enablerLogic.queryResourceManager(request);
        
        try {
            LOG.info("querying Paris temp resources: {}", new ObjectMapper().writeValueAsString(response));
        } catch (JsonProcessingException e) {
            LOG.error("Problem with deserializing ResourceManagerAcquisitionStartResponse", e);
        }
    }

    @Override
    public void notEnoughResources(NotEnoughResourcesAvailable notEnoughResourcesAvailable) {
        LOG.debug("Not enough resources");        
    }

    @Override
    public void resourcesUpdated(ResourcesUpdated resourcesUpdated) {
        LOG.debug("Resources updated from Enabler Resource Manager");
    }
}
