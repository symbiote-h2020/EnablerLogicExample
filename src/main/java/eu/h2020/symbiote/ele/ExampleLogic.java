package eu.h2020.symbiote.ele;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.h2020.symbiote.cloud.model.internal.CloudResource;
import eu.h2020.symbiote.core.internal.CoreQueryRequest;
import eu.h2020.symbiote.ele.model.MessageRequest;
import eu.h2020.symbiote.ele.model.MessageResponse;
import eu.h2020.symbiote.enabler.messaging.model.EnablerLogicDataAppearedMessage;
import eu.h2020.symbiote.enabler.messaging.model.NotEnoughResourcesAvailable;
import eu.h2020.symbiote.enabler.messaging.model.ResourceManagerAcquisitionStartResponse;
import eu.h2020.symbiote.enabler.messaging.model.ResourceManagerTaskInfoRequest;
import eu.h2020.symbiote.enabler.messaging.model.ResourcesUpdated;
import eu.h2020.symbiote.enablerlogic.EnablerLogic;
import eu.h2020.symbiote.enablerlogic.ProcessingLogic;
import eu.h2020.symbiote.enablerlogic.messaging.RegistrationHandlerClientService;
import eu.h2020.symbiote.enablerlogic.messaging.properties.EnablerLogicProperties;
import eu.h2020.symbiote.model.cim.Actuator;
import eu.h2020.symbiote.model.cim.Datatype;
import eu.h2020.symbiote.model.cim.Effect;
import eu.h2020.symbiote.model.cim.EnumRestriction;
import eu.h2020.symbiote.model.cim.FeatureOfInterest;
import eu.h2020.symbiote.model.cim.LengthRestriction;
import eu.h2020.symbiote.model.cim.Location;
import eu.h2020.symbiote.model.cim.Observation;
import eu.h2020.symbiote.model.cim.ObservationValue;
import eu.h2020.symbiote.model.cim.PrimitiveDatatype;
import eu.h2020.symbiote.model.cim.Property;
import eu.h2020.symbiote.model.cim.Restriction;
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
import eu.h2020.symbiote.security.accesspolicies.common.IAccessPolicySpecifier;
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

    @Override
    public void initialization(EnablerLogic enablerLogic) {
        this.enablerLogic = enablerLogic;

        registerResources();
        
        registerRapConsumers();
        
        //asyncCommunication();
        //syncCommunication();

        //queryFixedStations();
        //queryMobileStations();
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
	                        LOG.debug("Turning on light {}", resourceId);
	                    } else {
	                        LOG.debug("Turning off light {}", resourceId);
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
	                	Parameter parameter = parameters.get("inputParam1");
	                    Assert.notNull(parameter, "Capability 'inputParam1' is required.");
	                    Object objectValue = parameter.getValue();
	                    Assert.isInstanceOf(String.class, objectValue, "Parameter 'inputParam1' should be string of length form 2-10.");
	                    String value = (String) objectValue;
                        LOG.debug("Invoking service {} with param {}.", resourceId, value);
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
        		Integer.toString(new Random().nextInt(50) - 10), // random temp. 
                new Property("Humidity", "humidity_iri", Arrays.asList("Air humidity")), 
                new UnitOfMeasurement("C", "degree Celsius", "celsius_iri", Arrays.asList("")));
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
                LOG.debug("Atempting to register resources count {}.", i);
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
        actuator.setName("Enabler Logic Example Light 1");
        actuator.setDescription(Arrays.asList("This is light 1"));
        
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
        
        service.setName("Enabler Logic Example Light service 1");
        service.setDescription(Arrays.asList("This is light service 1 in Enabler Logic Example"));
        
        eu.h2020.symbiote.model.cim.Parameter parameter = new eu.h2020.symbiote.model.cim.Parameter();
        service.setParameters(Arrays.asList(parameter));

        parameter.setName("inputParam1");
        parameter.setMandatory(true);
        // restriction
        LengthRestriction restriction = new LengthRestriction();
        restriction.setMin(2);
        restriction.setMax(10);
		parameter.setRestrictions(Arrays.asList(restriction));
		
		PrimitiveDatatype datatype = new PrimitiveDatatype();
		datatype.setArray(false);
		datatype.setBaseDatatype("http://www.w3.org/2001/XMLSchema#string"); // "http:\/\/www.w3.org\/2001\/XMLSchema#string"
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
        System.out.println("received new Observations:\n"+dataAppeared);
    }
    
    private void queryFixedStations() {
        ResourceManagerTaskInfoRequest request = new ResourceManagerTaskInfoRequest();
        request.setTaskId("Vienna-Fixed");
        request.setEnablerLogicName("interpolator");
        request.setMinNoResources(1);
        request.setCachingInterval("P0000-00-00T00:10:00"); // 10 mins.
            // Although the sampling period is either 30 mins or 60 mins there is a transmit
            // delay.
            // If we miss one reading by just 1 second and we set the interval to 30 mins we
            // are always 29 mins and 59 late.
        CoreQueryRequest coreQueryRequest = new CoreQueryRequest();
        coreQueryRequest.setLocation_lat(48.208174);
        coreQueryRequest.setLocation_long(16.373819);
        coreQueryRequest.setMax_distance(10_000); // radius 10km
        coreQueryRequest.setObserved_property(Arrays.asList("NOx"));
        request.setCoreQueryRequest(coreQueryRequest);
        ResourceManagerAcquisitionStartResponse response = enablerLogic.queryResourceManager(request);

        try {
            LOG.info("querying fixed resources: {}", new ObjectMapper().writeValueAsString(response));
        } catch (JsonProcessingException e) {
            LOG.error("Problem with deserializing ResourceManagerAcquisitionStartResponse", e);
        }
    }

    private void queryMobileStations() {
        
        ResourceManagerTaskInfoRequest request = new ResourceManagerTaskInfoRequest();
        request.setTaskId("Vienna-Mobile");
        request.setEnablerLogicName("interpolator");
        request.setMinNoResources(1);
        request.setCachingInterval("P0000-00-00T00:01:00"); // 1 min

        CoreQueryRequest coreQueryRequest = new CoreQueryRequest();
        coreQueryRequest.setLocation_lat(48.208174);
        coreQueryRequest.setLocation_long(16.373819);
        coreQueryRequest.setMax_distance(10_000); // radius 10km
        coreQueryRequest.setObserved_property(Arrays.asList("NOx"));
        request.setCoreQueryRequest(coreQueryRequest);
        ResourceManagerAcquisitionStartResponse response = enablerLogic.queryResourceManager(request);

        try {
            LOG.info("querying mobile resources: {}", new ObjectMapper().writeValueAsString(response));
        } catch (JsonProcessingException e) {
            LOG.error("Problem with deserializing ResourceManagerAcquisitionStartResponse", e);
        }
    }

    @Override
    public void notEnoughResources(NotEnoughResourcesAvailable notEnoughResourcesAvailable) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void resourcesUpdated(ResourcesUpdated resourcesUpdated) {
        // TODO Auto-generated method stub
        
    }
}
