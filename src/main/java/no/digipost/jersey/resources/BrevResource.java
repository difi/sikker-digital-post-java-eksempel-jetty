package no.digipost.jersey.resources;

import com.sun.jersey.spi.container.ResourceFilters;
import no.digipost.jersey.filters.NoCacheResponseFilter;
import no.digipost.sdp.SendBrevService;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path("/")
@ResourceFilters(NoCacheResponseFilter.class)
public class BrevResource {

    private static final SendBrevService sendBrevService;

    static {
        sendBrevService = new SendBrevService();
        sendBrevService.start();
    }

    @GET
    @Path("startSending")
    @Produces(TEXT_PLAIN)
    public Response startSending(@DefaultValue("1000") @QueryParam("sendInterval") Integer sendIntervalMs) {
        sendBrevService.startSending(sendIntervalMs);
        return Response.ok("Ok, sending every " + sendIntervalMs + "ms.").build();
    }

    @GET
    @Path("stopSending")
    @Produces(TEXT_PLAIN)
    public Response startSending() {
        sendBrevService.stopSending();
        return Response.ok("Ok, no longer sending").build();
    }

    @GET
    @Path("status")
    @Produces(TEXT_PLAIN)
    public Response status() {
        return Response.ok(sendBrevService.getStatus()).build();
    }

    @GET
    @Path("queue")
    @Produces(TEXT_PLAIN)
    public Response queue() {
        return Response.ok(sendBrevService.getQueueStatus()).build();
    }

}
