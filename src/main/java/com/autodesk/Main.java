package com.autodesk;

import org.apache.commons.io.FileUtils;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.olingo.client.api.*;
import org.apache.olingo.client.api.communication.request.cud.ODataEntityCreateRequest;
import org.apache.olingo.client.api.communication.request.cud.ODataEntityUpdateRequest;
import org.apache.olingo.client.api.communication.request.cud.UpdateType;
import org.apache.olingo.client.api.communication.request.invoke.ODataInvokeRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntityRequest;
import org.apache.olingo.client.api.communication.response.*;
import org.apache.olingo.client.api.domain.*;
import org.apache.olingo.client.core.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.http.client.methods.HttpPost;

import static java.lang.System.*;
/**
 * Created by Jonathan Miao on 7/6/2015.
 */

public class Main {

    public static void main(String[] args) throws IOException, ParseException, InterruptedException {

        //get your ConsumerKey/ConsumerSecret at http://developer.autodesk.com
        final String token = getToken("you consumer key", "you consumer secret");

        final String AppPackageName = "MyTestPackage";
        final String ActivityName = "MyTestActivity";

        ODataClient client = ODataClientFactory.getClient();
        String serviceRoot = "https://developer.api.autodesk.com/autocad.io/us-east/v2/";

        createOrUpdatePackage("your auto-loadable zip file path", AppPackageName, client, serviceRoot, token);

        createActivityIfNotExisted(ActivityName, AppPackageName, client, serviceRoot, token);

        createNewWorkItem(ActivityName, client, serviceRoot, token);

        demoVersionControl(AppPackageName, client, serviceRoot, token);
    }

    //obtain authorization token
    static String getToken(final String consumerKey, final String consumerSecret) throws IOException, ParseException {
        final String url = "https://developer.api.autodesk.com/authentication/v1/authenticate";
        final HttpPost post = new HttpPost(url);
        List<NameValuePair> form = new ArrayList<NameValuePair>();
        form.add(new BasicNameValuePair("client_id", consumerKey));
        form.add(new BasicNameValuePair("client_secret", consumerSecret));
        form.add(new BasicNameValuePair("grant_type", "client_credentials"));
        post.setEntity(new UrlEncodedFormEntity(form, "UTF-8"));


        final HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response = client.execute(post);

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatusLine().getStatusCode());
        }

        final BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
        final JSONParser jsonParser = new JSONParser();
        final JSONObject jsonObj = (JSONObject) jsonParser.parse(br);
        return (String)jsonObj.get("token_type") + " " + (String)jsonObj.get("access_token");
    }

    static void createOrUpdatePackage(final String filepath, final String appPackageName, final ODataClient client, final String serviceRoot, final String token) throws IOException {

        out.println("Creating/Updating AppPackage...");
        // First step -- query for the url to upload the AppPackage file
        URI uploadUri = client.newURIBuilder(serviceRoot)
                .appendEntitySetSegment("AppPackages")
                .appendPropertySegment("Operations.GetUploadUrl").build();

        ODataInvokeRequest<ClientProperty> uploadUrlRequest = client.getInvokeRequestFactory().getFunctionInvokeRequest(uploadUri, ClientProperty.class);
        uploadUrlRequest.addCustomHeader("Authorization", token);
        ODataInvokeResponse<ClientProperty> urlResponse = uploadUrlRequest.execute();
        String resourceUri = urlResponse.getBody().getValue().toString();

        // upload package
        UploadObject(resourceUri, filepath);

        // create or update AppPackage -- first check if it exists
        URI appUri = client.newURIBuilder(serviceRoot)
                .appendEntitySetSegment("AppPackages")
                .appendKeySegment(appPackageName).build();
        ODataEntityRequest<ClientEntity> entityRequest = client.getRetrieveRequestFactory().getEntityRequest(appUri);
        entityRequest.addCustomHeader("Authorization", token);
        ODataRetrieveResponse<ClientEntity> response = null;
        try {
            response = entityRequest.execute();
        } catch (Exception e) {};

        // create a new AppPackage
        ClientEntity appPackage = client.getObjectFactory().newEntity(new FullQualifiedName("ACES.Models", "AppPackage"));
        if (response == null) // create it
        {
            // set ID to ""
            appPackage.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
                    "Id", client.getObjectFactory().newPrimitiveValueBuilder().buildString(appPackageName)));
            // set RequiredEngineVersion to "20.1"
            appPackage.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
                    "RequiredEngineVersion", client.getObjectFactory().newPrimitiveValueBuilder().buildString("20.1")));
            appPackage.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
                    "Resource", client.getObjectFactory().newPrimitiveValueBuilder().buildString(resourceUri)));

            // submit the request
            URI uri = client.newURIBuilder(serviceRoot).appendEntitySetSegment("AppPackages").build();
            ODataEntityCreateRequest<ClientEntity> req = client.getCUDRequestFactory().getEntityCreateRequest(uri, appPackage);
            req.addCustomHeader("Authorization", token);
            ODataEntityCreateResponse<ClientEntity> res = req.execute();
            out.println("Returned response code for Creating AppPackage: " + res.getStatusCode());
        } else { // update it
            appPackage.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
                    "Resource", client.getObjectFactory().newPrimitiveValueBuilder().buildString(resourceUri)));

            // submit the request
            URI uri = client.newURIBuilder(serviceRoot).appendEntitySetSegment("AppPackages").appendKeySegment(appPackageName).build();
            ODataEntityUpdateRequest<ClientEntity> req = client.getCUDRequestFactory().getEntityUpdateRequest(uri, UpdateType.PATCH, appPackage);
            req.addCustomHeader("Authorization", token);
            ODataEntityUpdateResponse<ClientEntity> res = req.execute();
            out.println("Returned response code for Updating AppPackage: " + res.getStatusCode());
        }
    }

    public static void UploadObject(String uploadUrl, String fileName) throws IOException
    {
        URL url = new URL(uploadUrl);
        HttpURLConnection connection=(HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("PUT");
        OutputStream os = connection.getOutputStream();

        byte[] buffer = new byte[8000];
        File file = new File(fileName);
        InputStream is = new FileInputStream(file);
        int length = 0;
        while((length=is.read(buffer))>0)
        {
            os.write(buffer, 0, length);
        }
        os.close();

        int responseCode = connection.getResponseCode();
        out.println("Returned response code for uploading file: " + responseCode);
    }

    static void createActivityIfNotExisted(final String activityName, final String appPackName, final ODataClient client, final String serviceRoot, final String token)
    {
        URI actUri = client.newURIBuilder(serviceRoot)
                .appendEntitySetSegment("Activities")
                .appendKeySegment(activityName).build();
        ODataEntityRequest<ClientEntity> entityRequest = client.getRetrieveRequestFactory().getEntityRequest(actUri);
        entityRequest.addCustomHeader("Authorization", token);

        ODataRetrieveResponse<ClientEntity> response = null;
        try { response = entityRequest.execute(); } catch(Exception e){};
        if(response == null)
        {
            out.println("Creating Activity...");
            ClientEntity activity = client.getObjectFactory().newEntity(new FullQualifiedName("ACES.Models", "Activity"));
            // set ID to ""
            activity.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
                    "Id", client.getObjectFactory().newPrimitiveValueBuilder().buildString(activityName)));
            // set RequiredEngineVersion to "20.1"
            activity.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
                    "RequiredEngineVersion", client.getObjectFactory().newPrimitiveValueBuilder().buildString("20.1")));
            // set Instruction
            ClientComplexValue instruction = client.getObjectFactory().newComplexValue("ACES.Models.Instruction");
            instruction.add(client.getObjectFactory().newPrimitiveProperty("Script",
                    client.getObjectFactory().newPrimitiveValueBuilder().buildString("_test params.json outputs\n")));
            activity.getProperties().add(client.getObjectFactory().newComplexProperty(
                    "Instruction", instruction));

            // set Parameters
            ClientComplexValue inputParam1 = client.getObjectFactory().newComplexValue("ACES.Models.Parameter");
            inputParam1.add(client.getObjectFactory().newPrimitiveProperty(
                    "Name", client.getObjectFactory().newPrimitiveValueBuilder().buildString("HostDwg")));
            inputParam1.add(client.getObjectFactory().newPrimitiveProperty(
                    "LocalFileName", client.getObjectFactory().newPrimitiveValueBuilder().buildString("$(HostDwg)")));
            ClientComplexValue inputParam2 = client.getObjectFactory().newComplexValue("ACES.Models.Parameter");
            inputParam2.add(client.getObjectFactory().newPrimitiveProperty(
                    "Name", client.getObjectFactory().newPrimitiveValueBuilder().buildString("Params")));
            inputParam2.add(client.getObjectFactory().newPrimitiveProperty(
                    "LocalFileName", client.getObjectFactory().newPrimitiveValueBuilder().buildString("params.json")));
            ClientCollectionValue<ClientValue> inputParamCollection = client.getObjectFactory().newCollectionValue("ACES.Models.Parameter");
            inputParamCollection.add(inputParam1);
            inputParamCollection.add(inputParam2);

            ClientComplexValue outputParam1 = client.getObjectFactory().newComplexValue("ACES.Models.Parameter");
            outputParam1.add(client.getObjectFactory().newPrimitiveProperty(
                    "Name", client.getObjectFactory().newPrimitiveValueBuilder().buildString("Results")));
            outputParam1.add(client.getObjectFactory().newPrimitiveProperty(
                    "LocalFileName", client.getObjectFactory().newPrimitiveValueBuilder().buildString("outputs")));
            ClientCollectionValue<ClientValue> outputParamCollection = client.getObjectFactory().newCollectionValue("ACES.Models.Parameter");
            outputParamCollection.add(outputParam1);

            ClientComplexValue parametersCollection = client.getObjectFactory().newComplexValue("ACES.Models.Parameters");
            parametersCollection.add(client.getObjectFactory().newCollectionProperty(
                    "InputParameters", inputParamCollection));
            parametersCollection.add(client.getObjectFactory().newCollectionProperty(
                    "OutputParameters", outputParamCollection));
            activity.getProperties().add(client.getObjectFactory().newComplexProperty("Parameters", parametersCollection));

            // set AppPackages
            ClientCollectionValue appPackages = client.getObjectFactory().newCollectionValue("String");
            appPackages.add(client.getObjectFactory().newPrimitiveValueBuilder().buildString(appPackName));
            activity.getProperties().add(client.getObjectFactory().newCollectionProperty("AppPackages", appPackages));

            // submit the request
            URI uri = client.newURIBuilder(serviceRoot).appendEntitySetSegment("Activities").build();
            ODataEntityCreateRequest<ClientEntity> req = client.getCUDRequestFactory().getEntityCreateRequest(uri, activity);
            req.addCustomHeader("Authorization", token);
            ODataEntityCreateResponse<ClientEntity> res = req.execute();
            out.println("Returned response code for Creating Activity: " + res.getStatusCode());
        }
    }

    static void createNewWorkItem(final String activityId, final ODataClient client, final String serviceRoot, final String token) throws InterruptedException, IOException, ParseException {
        // create a new WorkItem
        ClientEntity ent = client.getObjectFactory().newEntity(new FullQualifiedName("ACES.Models", "WorkItem"));
        // set ID to ""
        ent.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
                "Id", client.getObjectFactory().newPrimitiveValueBuilder().buildString("")));

        // set ActivityId
        ent.getProperties().add(client.getObjectFactory().newPrimitiveProperty(
                "ActivityId", client.getObjectFactory().newPrimitiveValueBuilder().buildString(activityId)));

        // Set Arguments
        ClientComplexValue inputArgument1 = client.getObjectFactory().newComplexValue("ACES.Models.Argument");
        inputArgument1.add(client.getObjectFactory().newPrimitiveProperty("Name",
                client.getObjectFactory().newPrimitiveValueBuilder().buildString("HostDwg")));
        inputArgument1.add(client.getObjectFactory().newPrimitiveProperty("Resource",
                client.getObjectFactory().newPrimitiveValueBuilder().buildString("http://download.autodesk.com/us/samplefiles/acad/blocks_and_tables_-_imperial.dwg")));
        inputArgument1.add(client.getObjectFactory().newEnumProperty("StorageProvider", client.getObjectFactory().newEnumValue("ACES.Models.StorageProvider", "Generic")));

        ClientComplexValue inputArgument2 = client.getObjectFactory().newComplexValue("ACES.Models.Argument");
        inputArgument2.add(client.getObjectFactory().newEnumProperty("ResourceKind",
                client.getObjectFactory().newEnumValue("ACES.Models.ResourceKind", "Embedded")));
        inputArgument2.add(client.getObjectFactory().newPrimitiveProperty("Name",
                client.getObjectFactory().newPrimitiveValueBuilder().buildString("Params")));
        inputArgument2.add(client.getObjectFactory().newPrimitiveProperty("Resource",
                client.getObjectFactory().newPrimitiveValueBuilder().buildString("data:application/json, {\"ExtractBlockNames\":true,\"ExtractLayerNames\":true}")));
        inputArgument2.add(client.getObjectFactory().newEnumProperty("StorageProvider", client.getObjectFactory().newEnumValue("ACES.Models.StorageProvider", "Generic")));

        ClientComplexValue outputArgument = client.getObjectFactory().newComplexValue("ACES.Models.Argument");
        outputArgument.add(client.getObjectFactory().newPrimitiveProperty("Name",
                client.getObjectFactory().newPrimitiveValueBuilder().buildString("Results")));
        outputArgument.add(client.getObjectFactory().newPrimitiveProperty("Resource",
                client.getObjectFactory().newPrimitiveValueBuilder().buildString("")));
        outputArgument.add(client.getObjectFactory().newEnumProperty("StorageProvider", client.getObjectFactory().newEnumValue("ACES.Models.StorageProvider", "Generic")));
        outputArgument.add(client.getObjectFactory().newEnumProperty("HttpVerb", client.getObjectFactory().newEnumValue("ACES.Models.HttpVerbType", "POST")));
        outputArgument.add(client.getObjectFactory().newEnumProperty("ResourceKind", client.getObjectFactory().newEnumValue("ACES.Models.ResourceKind", "ZipPackage")));

        ClientCollectionValue<ClientValue> inputArgCollection = client.getObjectFactory().newCollectionValue("ACES.Models.Argument");
        inputArgCollection.add(inputArgument1);
        inputArgCollection.add(inputArgument2);
        ClientCollectionValue<ClientValue> outputArgCollection = client.getObjectFactory().newCollectionValue("ACES.Models.Argument");
        outputArgCollection.add(outputArgument);
        ClientComplexValue arguments = client.getObjectFactory().newComplexValue("ACES.Models.Arguments");
        arguments.add(client.getObjectFactory().newCollectionProperty("InputArguments", inputArgCollection));
        arguments.add(client.getObjectFactory().newCollectionProperty("OutputArguments", outputArgCollection));

        ent.getProperties().add(client.getObjectFactory().newComplexProperty("Arguments", arguments));

        // submit the request
        URI uri = client.newURIBuilder(serviceRoot).appendEntitySetSegment("WorkItems").build();
        ODataEntityCreateRequest<ClientEntity> req = client.getCUDRequestFactory().getEntityCreateRequest(uri, ent);
        req.addCustomHeader("Authorization", token);
        ODataEntityCreateResponse<ClientEntity> res = req.execute();

        if (res.getStatusCode() == 201) {
            ClientEntity workItem = res.getBody();
            String id = workItem.getProperty("Id").getValue().asPrimitive().toString();
            String status;
            do {
                out.println("Sleeping for 2s...");
                Thread.sleep(2000);
                out.print("Checking work item status=");
                workItem = pollWorkItem(client, serviceRoot, token, id);
                status = workItem.getProperty("Status").getValue().asEnum().getValue();
                out.println(status);
            } while (status.compareTo("Pending") == 0 || status.compareTo("InProgress") == 0);
            if (status.compareTo("Succeeded") == 0)
                downloadResults(workItem);
        }
    }

    //polls the workitem for its status. Returns the status.
    static ClientEntity pollWorkItem(final ODataClient client, final String serviceRoot, final String token, final String id) throws IOException, ParseException {
        // try get Activity
        URI uri = client.newURIBuilder(serviceRoot)
                .appendEntitySetSegment("WorkItems")
                .appendKeySegment(id).build();

        ODataEntityRequest<ClientEntity> entityRequest = client.getRetrieveRequestFactory().getEntityRequest(uri);
        entityRequest.addCustomHeader("Authorization", token);
        ODataRetrieveResponse<ClientEntity> response = entityRequest.execute();
        ClientEntity wi = response.getBody();
        return wi;
    }

    //downloads the workitem results and status report.
    static void downloadResults(final ClientEntity wi) throws IOException, ParseException {
        ClientComplexValue arguments = wi.getProperty("Arguments").getComplexValue();
        ClientProperty outputArguments = arguments.get("OutputArguments");
        for(ClientValue collectionValue : outputArguments.getCollectionValue())
        {
            ClientComplexValue argument = collectionValue.asComplex();
            String nameValue = argument.get("Name").getValue().asPrimitive().toString();
            if(nameValue.compareTo("Result")==0) {
                final String resultUrl = argument.get("Resource").getValue().asPrimitive().toString();
                // download result pdf
                FileUtils.copyURLToFile(new URL(resultUrl), new File("d:/result.pdf"));
                break;
            }
        }
        final String reportUrl = wi.getProperty("StatusDetails").getComplexValue().get("Report").getValue().asPrimitive().toString();
        // download execution report
        FileUtils.copyURLToFile(new URL(reportUrl), new File("d:/report.txt"));
    }

    static void demoVersionControl(final String appPackName, final ODataClient client, final String serviceRoot, final String token)
    {
        // We have version control over submitted AppPackages/Activities.
        out.println("We have version control over submitted AppPackages/Activities. Here is the version history of AppPackage with name: " + appPackName);

        URI getVersionsUri = client.newURIBuilder(serviceRoot)
                .appendEntitySetSegment("AppPackages")
                .appendKeySegment(appPackName)
                .appendPropertySegment("Operations.GetVersions").build();

        ODataInvokeRequest<ClientProperty> getVersionsRequest = client.getInvokeRequestFactory().getFunctionInvokeRequest(getVersionsUri, ClientProperty.class);
        getVersionsRequest.addCustomHeader("Authorization", token);
        ODataInvokeResponse<ClientProperty> getVersionsResponse = getVersionsRequest.execute();
        ClientCollectionValue<ClientValue> versionedAppPackages = getVersionsResponse.getBody().getCollectionValue();
        List<Integer> verList = new ArrayList<Integer>();
        for(ClientValue cv : versionedAppPackages)
        {
            ClientComplexValue appPackage = cv.asComplex();
            int ver = (Integer) ( appPackage.get("Version").getPrimitiveValue().toValue());
            String time = appPackage.get("Timestamp").getPrimitiveValue().toString();
            out.println("Version #: "+ ver + ".  Time Submitted: " + time);
            verList.add(ver);
        }
        Collections.sort(verList);
        Integer smallestVer = verList.get(0);

        try {
            URI setVersionUri = client.newURIBuilder(serviceRoot)
                    .appendEntitySetSegment("AppPackages")
                    .appendKeySegment(appPackName)
                    .appendPropertySegment("Operations.SetVersion")
                    .build();
            ODataInvokeRequest<ClientProperty> setVersionRequest = client.getInvokeRequestFactory().getActionInvokeRequest(setVersionUri, ClientProperty.class);
            Map<String, ClientValue> content = new HashMap<String, ClientValue>();
            content.put("Version", client.getObjectFactory().newPrimitiveValueBuilder().buildString(smallestVer.toString()));
            setVersionRequest.setParameters(content);
            HttpMethod p = setVersionRequest.getMethod();
            setVersionRequest.addCustomHeader("Authorization", token);
            ODataInvokeResponse serVersionResponse = setVersionRequest.execute();
            out.println(serVersionResponse.getStatusCode());
        }
        catch(Exception e )
        {
            out.println(e.getMessage());
            out.println(e.getCause().getMessage());
        }
    }

}
