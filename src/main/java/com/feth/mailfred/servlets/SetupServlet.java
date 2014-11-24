package com.feth.mailfred.servlets;

import com.feth.mailfred.util.Utils;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.extensions.appengine.auth.oauth2.AbstractAppEngineAuthorizationCodeServlet;
import com.google.appengine.api.users.UserServiceFactory;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SetupServlet extends AbstractAppEngineAuthorizationCodeServlet {

    public static final String PARAMETER_FORMAT = "format";
    public static final String FORMAT_JSON = "json";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (FORMAT_JSON.equals(request.getParameter(PARAMETER_FORMAT))) {
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("application/json");
            final JSONObject obj = new JSONObject();
            obj.put("success", true);
            obj.put("error", false);
            obj.write(response.getWriter());
        } else {
            response.sendRedirect("/");
        }
    }

    @Override
    protected String getRedirectUri(HttpServletRequest req) throws ServletException, IOException {
        return Utils.getRedirectUri(req);
    }

    @Override
    protected AuthorizationCodeFlow initializeFlow() throws IOException {
        final String userId = UserServiceFactory.getUserService().getCurrentUser().getUserId();
        return Utils.newFlow(userId);
    }
}
