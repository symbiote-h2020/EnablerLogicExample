package eu.h2020.symbiote.ele.model;

public class MessageResponse {
    private String response;

    public MessageResponse() {
    }
    
    public MessageResponse(String response) {
        this.response = response;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
}
