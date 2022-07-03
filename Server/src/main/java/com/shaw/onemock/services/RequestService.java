package com.shaw.onemock.services;

import com.shaw.onemock.constants.CaptureState;
import com.shaw.onemock.constants.GlobalConstants;
import com.shaw.onemock.constants.MockPathHolder;
import com.shaw.onemock.dtos.utils.ResponseModel;
import com.shaw.onemock.models.mock.MockRequest;
import com.shaw.onemock.models.requests.Header;
import com.shaw.onemock.models.requests.Request;
import com.shaw.onemock.repositories.mock.MockRequestRepository;
import com.shaw.onemock.repositories.request.HeaderRepository;
import com.shaw.onemock.repositories.request.RequestRepository;
import com.shaw.onemock.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class RequestService {
    @Autowired
    private RequestRepository repository;
    @Autowired
    private HeaderRepository headerRepository;
    @Autowired
    private MockRequestRepository mockRequestRepository;
    @Autowired
    private MockPathHolder mockPathHolder;

    public Set<Header> getHeaders(HttpServletRequest request) {
        Set<Header> headers = new HashSet<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerKey = headerNames.nextElement();
                String headerValue = request.getHeader(headerKey);
                Header headerEntity = headerRepository
                        .findByKeyAndValue(headerKey, headerValue)
                        .orElseGet(() -> new Header(headerKey, headerValue));
                headers.add(headerEntity);
            }
        }

        return headers;
    }

    public void saveRequest(HttpServletRequest request, String path, String params, String body) {
        DateFormat outputFormat = new SimpleDateFormat("HH:mm:ss");
        Request requestEntity = new Request(body, path, request.getMethod(), params, outputFormat.format(new Date()));
        Set<Header> headersList = getHeaders(request);
        headerRepository.saveAll(headersList);
        requestEntity.setHeaders(headersList);
        CaptureState.setLastId(repository.save(requestEntity).getRequestId());
    }

    public Long matchMockPathPool(String path) {
        System.out.println(path);
        for (Pair<Long, String> regexPath : mockPathHolder.getPaths()) {
            if (path.matches(regexPath.getSecond())) {
                return regexPath.getFirst();
            }
        }
        return 0L;
    }


    public ResponseEntity<String> process(HttpServletRequest request, String path) {
        String response = GlobalConstants.DEFAULT_RESPONSE;
        Integer statusCode = GlobalConstants.DEFAULT_RESPONSE_STATUS;
        MediaType contentType = MediaType.TEXT_PLAIN;
        String body = Utils.getBody(request);
        List<String> paths = Arrays.asList(path.split("/?"));
        if (CaptureState.getCapture()) {
            saveRequest(request, paths.get(0), paths.size() == 2 ? paths.get(1) : "", body);
        }

        Long mockId = matchMockPathPool(path);
        Optional<MockRequest> mockRequestOptional = mockRequestRepository.findById(mockId);
        if (mockRequestOptional.isPresent()) {
            MockRequest mockRequest = mockRequestOptional.get();
            if (mockRequest.getHasMultipleResponse()) {
                ResponseModel data = Utils.getCustomResponse(mockRequest.getCustomResponses(), body, request);
                response = data.getResponseBody();
                statusCode = data.getStatusCode();
                contentType = data.getFormat();
            } else {
                response = mockRequest.getResponseBody();
                statusCode = mockRequest.getStatusCode();
                contentType = ResponseModel.getMediaType(mockRequest.getFormat());
            }
        }
        return ResponseEntity.status(statusCode).contentType(contentType).body(response);
    }
}
