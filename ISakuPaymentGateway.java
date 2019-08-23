/* Copyright PT. Indo Lotte Makmur, Inc - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited Proprietary and confidential
 * 
 * Written by ECM project team
 */
package com.indolotte.ifc.mq.biz.op.service.payment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.indolotte.ConstCode;
import com.indolotte.Constants;
import com.indolotte.OrderConstants;
import com.indolotte.base.config.DomainsConfig;
import com.indolotte.base.config.EnvConfig;
import com.indolotte.base.exception.RuntimeFailException;
import com.indolotte.base.mvc.AbstractService;
import com.indolotte.biz.op.service.OrderPaymentProgress;
import com.indolotte.entity.api.op.CancellationRequestRVO;
import com.indolotte.entity.biz.op.OrderPaymentProgessEVO;
import com.indolotte.ifc.mq.biz.op.api.PaymentIfLocal;
import com.indolotte.ifc.mq.biz.op.service.InterfaceService;
import com.indolotte.ifc.mq.biz.op.service.OrderIf;
import com.indolotte.ifc.mq.biz.op.service.ProcessDynamoDB;
import com.indolotte.ifc.mq.entity.api.op.order.OrderFormSVO;
import com.indolotte.ifc.mq.entity.api.op.payment.ISakuRequestRVO;
import com.indolotte.ifc.mq.entity.api.op.payment.PaymentSVO;
import com.indolotte.ifc.mq.entity.biz.op.ISakuPaymentReceiveEVO;
import com.indolotte.ifc.mq.entity.biz.op.ISakuPaymentRefundRequestEVO;
import com.indolotte.ifc.mq.entity.biz.op.ISakuPaymentRequestEVO;
import com.indolotte.ifc.mq.entity.biz.op.payment.PaymentInformationEVO;
import com.indolotte.util.DateUtil;

import net.sf.json.JSONObject;

@Service
@Lazy
public class ISakuPaymentGateway extends AbstractService {

    private static final String INTERFACE = "INTERFACE";
    private static final String UTF8 = "UTF-8";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String METHOD_POST = "POST";

    private static final boolean INPUT_OUTPUT = true;

    private static final String ISAKU_STORE_CODE = "ILTT";
    private static final String ISAKU_TRANSACTION_TYPE_PURCHASE = "PURCHASE";
    private static final String ISAKU_TRANSACTION_TYPE_REVERSAL_PURCHASE = "REVERSAL_PURCHASE";
    private static final String ISAKU_TRANSACTION_TYPE_REFUND = "REFUND";

    private static final String ISAKU_MAP_KEY_TIMESTAMP = "Timestamp";
    private static final String ISAKU_MAP_KEY_CLIENT_ID = "ClientID";
    private static final String ISAKU_MAP_KEY = "Key";
    private static final String ISAKU_MAP_KEY_BRANCH_ID = "BranchID";
    private static final String ISAKU_MAP_KEY_COUNTER_ID = "CounterID";
    private static final String ISAKU_MAP_KEY_KASIR = "Kasir";
    private static final String ISAKU_MAP_KEY_PRODUCT_TYPE = "ProductType";
    private static final String ISAKU_MAP_KEY_TRANSACTION_TYPE = "TrxType";

    private static final String ISAKU_MAP_KEY_DETAIL = "Detail";
    private static final String ISAKU_MAP_KEY_DETAIL_TRANSACTION_ID = "trxId";
    private static final String ISAKU_MAP_KEY_DETAIL_TRANSACTION_DATE = "trxDate";
    private static final String ISAKU_MAP_KEY_DETAIL_TERMINAL = "Terminal";
    private static final String ISAKU_MAP_KEY_DETAIL_TOKEN = "Token";
    private static final String ISAKU_MAP_KEY_DETAIL_NO_HP = "noHP";
    private static final String ISAKU_MAP_KEY_DETAIL_AMOUNT = "Amount";

    private static final String ISAKU_MAP_KEY_TIMEOUT = "Timeout";
    private static final String ISAKU_MAP_KEY_VERSI_PROGRAM = "VersiProgram";
    private static final String ISAKU_MAP_KEY_RESPONSE_CODE = "RespCode";
    private static final String ISAKU_MAP_KEY_RESPONSE_DETAIL = "RespDetail";

    private static final String ISAKU_MAP_KEY_TRANSACTION_CONFIRM = "TrxConfirm";
    private static final String ISAKU_MAP_KEY_FEE = "Fee";
    private static final String ISAKU_MAP_KEY_ID_ISAKU = "ID_isaku";

    private static final String ISAKU_TRANSACTION_PURCHASE_ID = "trx_id_purchase";
    private static final String ISAKU_TRANSACTION_ID = "trx_id";
    private static final String ISAKU_DEPOSITE_TYPE = "tipe_deposit";
    private static final String ISAKU_MAP_KEY_DETAIL_NOMINAL = "Nominal";
    private static final String ISAKU_DEPOSITE_TYPE_VALUE = "CASH IN";
    private static final String ISAKU_CASHIN_CODE = "kode_cashin";
    private static final String ISAKU_PATNER_CODE = "kode_partner";
    private static final String ISAKU_CASHIN_CODE_VALUE = "CASHINREFUND00000001";
    private static final String ISAKU_PATNER_CODE_VALUE = "DEPR0007";

    private static final String STATUS_SUCCESS = "00";
    private static final String STATUS_FAIL = "99";
    private static final String STATUS_TIMEOUT = "TIMEOUT";
    private static final String STATUS_WRG_FRM_REQ = "i44";
    private static final String STATUS_INV_KEY_REQ = "i97";
    private static final String STATUS_PRD_TYP_UND = "i29";
    private static final String STATUS_TRX_TYP_UND = "i30";
    private static final String STATUS_TRX_ID_INV = "i88";
    private static final String STATUS_ERR_UND = "i66";
    private static final String STATUS_SVR_MTC = "i11";

    private String transactionId = "";
    private String transactionDate = "";
    private String sendUrl = "";

    @Autowired
    protected EnvConfig envConfig;

    @Autowired
    protected DomainsConfig domainsConfig;

    @Autowired
    protected OrderPaymentProgress orderPaymentProgress;

    @Autowired
    protected InterfaceService interfaceService;

    @Autowired
    protected PaymentIfLocal paymentIfLocal;

    @Autowired
    protected OrderIf orderIf;

    @Autowired
    protected ProcessDynamoDB processDynamoDB;

    public ISakuRequestRVO executeApprove(OrderFormSVO orderFormSVO) {
        LOGGER.info("interface - executeApprovePG - e-isaku - start");

        String responsePurchase = "";
        String responseReversalPurchase = "";

        JSONObject returnJson = null;

        String generateTrxDate = DateUtil.today("YYMMddHHmmssSSSSS");
        transactionDate = DateUtil.today("YYYYMMddHHmmss");
        transactionId = ISAKU_STORE_CODE + generateTrxDate;

        LOGGER.info("interface - e-isaku - transactionId " + transactionId);

        PaymentSVO payment = orderFormSVO.getPaymentSVO();

        String tokenNumber = payment.getOnePassTokenNumber();
        String orderNumber = payment.getOrderNumber();
        Long orderAmount = payment.getTotalPaymentAmount();
        String channelId = payment.getPgChannelId();

        LOGGER.info("interface - e-isaku - tokenNumber " + tokenNumber);
        LOGGER.info("interface - e-isaku - orderNumber " + orderNumber);
        LOGGER.info("interface - e-isaku - orderAmount " + orderAmount);
        LOGGER.info("interface - e-isaku - channelId " + channelId);

        ISakuRequestRVO rvo = new ISakuRequestRVO();
        rvo.setTransactionId(transactionId);
        rvo.setGrossAmount(orderAmount);
        rvo.setPgCompanyCode(OrderConstants.PG_COMPANY_CODE_ISAKU);
        rvo.setPaymentMethodDetail(OrderConstants.PAYMENT_DETAIL_METHOD_CODE_ISAKU);
        rvo.setOrderNumber(orderNumber);
        rvo.setChannelId(channelId);
        rvo.setToken(tokenNumber);

        JSONObject detail = new JSONObject();
        detail.put(ISAKU_MAP_KEY_DETAIL_TRANSACTION_ID, transactionId);
        detail.put(ISAKU_MAP_KEY_DETAIL_TRANSACTION_DATE, transactionDate);
        detail.put(ISAKU_MAP_KEY_DETAIL_TERMINAL, "");
        detail.put(ISAKU_MAP_KEY_DETAIL_TOKEN, tokenNumber);
        detail.put(ISAKU_MAP_KEY_DETAIL_NO_HP, "");
        detail.put(ISAKU_MAP_KEY_DETAIL_AMOUNT, "" + orderAmount);

        JSONObject header = setJsonObject(ISAKU_TRANSACTION_TYPE_PURCHASE, detail);

        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("data", header.toString()));

        sendUrl = domainsConfig.get("isaku.url");

        //custom field req, 1 for type trx, 2 for detail json, 3 for token number
        rvo.setCustomField1(ISAKU_TRANSACTION_TYPE_PURCHASE);
        rvo.setCustomField2(detail.toString());
        rvo.setCustomField3(tokenNumber);

        insertInterfacePaymentRequest(rvo);

        updateOrderPaymentProgress(orderFormSVO.getPaymentSVO(),
                OrderConstants.ORDER_PAYMENT_PROGRESS_STATUS_CODE_PG_START);

        try {
            LOGGER.info("interface - e-isaku - requestP " + urlParameters);
            returnJson = sendHttp(sendUrl, urlParameters);
            LOGGER.info("interface - e-isaku - responseP " + returnJson);

            rvo.setCustomField2(returnJson.getString(ISAKU_MAP_KEY_DETAIL));
            rvo.setPgPaymentMessage(returnJson.getString(ISAKU_MAP_KEY_RESPONSE_DETAIL));
            responsePurchase = returnJson.getString(ISAKU_MAP_KEY_RESPONSE_CODE);
            rvo.setPgPaymentStatus(responsePurchase);
            //            rvo.setCustomField3();

            insertInterfacePaymentReceive(rvo, true);
        } catch (Exception e) {
            LOGGER.error("interface - executeApprovePG - e-isaku - error : [" + e.getCause() + "]");
            LOGGER.info("interface - executeApprovePG - e-isaku - error " + e);
            insertInterfacePaymentReceive(rvo, false);

            responseReversalPurchase = executeReversalPurchase(rvo);
        }

        //        rvo.setRespCode(returnJson.getString(ISAKU_MAP_KEY_RESPONSE_CODE));

        if (STATUS_SUCCESS.equals(responsePurchase) || STATUS_SUCCESS.equals(responseReversalPurchase)) {
            updateOrderPaymentProgress(orderFormSVO.getPaymentSVO(),
                    OrderConstants.ORDER_PAYMENT_PROGRESS_STATUS_CODE_PG_COMPLETE);
            rvo.setPgPaymentStatus(STATUS_SUCCESS);
        } else {
            updateOrderPaymentProgress(orderFormSVO.getPaymentSVO(),
                    OrderConstants.ORDER_PAYMENT_PROGRESS_STATUS_CODE_PG_ERROR);
            rvo.setPgPaymentStatus(STATUS_FAIL);
        }

        LOGGER.info("interface - executeApprovePG - e-isaku - end");

        return rvo;
    }

    private ISakuPaymentRequestEVO insertInterfacePaymentRequest(ISakuRequestRVO rvo) {
        ISakuPaymentRequestEVO evo = new ISakuPaymentRequestEVO();

        String id = INTERFACE;
        evo.setInterfaceNumber("EC_OP_I0041");
        evo.setBusinessModuleCode("OP");
        evo.setInterfaceTypeCode(ConstCode.IF_TYCD_REALTIME);
        evo.setInterfaceSendingAndReceivingTypeCode(ConstCode.IF_SNR_TYCD_SEND);
        evo.setInterfaceProgressStatusCode(ConstCode.IF_PROG_STCD_PROGRESS);
        evo.setInterfaceExecutorId(id);
        evo.setInterfaceExecutionCountNumber(1);
        evo.setInterfaceExecutionResultMessageContent("");
        evo.setFirstRegistrationPersonId(id);
        evo.setLastModificationPersonId(id);

        evo.setGrossAmount(rvo.getGrossAmount());
        evo.setPgCompanyCode(rvo.getPgCompanyCode());
        evo.setPaymentMethodDetail(rvo.getPaymentMethodDetail());
        evo.setTransactionId(rvo.getTransactionId());
        evo.setOrderNumber(rvo.getOrderNumber());
        evo.setChannelId(rvo.getChannelId());
        evo.setCustomField1(rvo.getCustomField1());
        evo.setCustomField2(rvo.getCustomField2());
        evo.setCustomField3(rvo.getCustomField3());

        LOGGER.debug("interface - e-isaku - insertReq: {}", evo);
        interfaceService.insertISakuPaymentRequest(evo);

        return evo;
    }

    private void insertInterfacePaymentReceive(ISakuRequestRVO rvo, boolean isSuccess) {
        ISakuPaymentReceiveEVO evo = new ISakuPaymentReceiveEVO();

        String id = INTERFACE;
        evo.setInterfaceNumber("EC_OP_I0041");
        evo.setBusinessModuleCode("OP");
        evo.setInterfaceTypeCode(ConstCode.IF_TYCD_REALTIME);
        evo.setInterfaceSendingAndReceivingTypeCode(ConstCode.IF_SNR_TYCD_RCV);

        if (isSuccess) {
            evo.setInterfaceProgressStatusCode(ConstCode.IF_PROG_STCD_SUCCESS);
        } else {
            evo.setInterfaceProgressStatusCode(ConstCode.IF_PROG_STCD_FAIL);
        }

        if (null != rvo.getPgPaymentStatus() && STATUS_SUCCESS.equals(rvo.getPgPaymentStatus())) {
            evo.setPaymentStatusCode("00");
            evo.setPgPaymentStatus(rvo.getPgPaymentStatus());
        } else {
            evo.setPaymentStatusCode(STATUS_FAIL);
            evo.setPgPaymentStatus(rvo.getPgPaymentStatus());
        }

        evo.setPgPaymentMessage(rvo.getPgPaymentMessage());

        evo.setInterfaceExecutorId(id);
        evo.setInterfaceExecutionCountNumber(1);
        evo.setInterfaceExecutionResultMessageContent("");
        evo.setFirstRegistrationPersonId(id);
        evo.setLastModificationPersonId(id);

        evo.setGrossAmount(rvo.getGrossAmount());
        evo.setPgCompanyCode(rvo.getPgCompanyCode());
        evo.setPaymentMethodDetail(rvo.getPaymentMethodDetail());
        evo.setTransactionId(rvo.getTransactionId());
        evo.setOrderNumber(rvo.getOrderNumber());
        evo.setChannelId(rvo.getChannelId());
        evo.setCustomField1(rvo.getCustomField1());
        evo.setCustomField2(rvo.getCustomField2());
        evo.setCustomField3(rvo.getCustomField3());

        LOGGER.debug("interface - e-isaku - insertRcv: {}", evo);
        interfaceService.insertISakuPaymentReceive(evo);
    }

    protected void updateOrderPaymentProgress(PaymentSVO paymentSVO, String orderPaymentProgressStatusCode) {
        OrderPaymentProgessEVO evo = new OrderPaymentProgessEVO();
        evo.setOrderNumber(paymentSVO.getOrderNumber());
        evo.setOrderPaymentProgressStatusCode(orderPaymentProgressStatusCode);
        evo.setPaymentMethodCode(paymentSVO.getPaymentMethod().getValue());
        evo.setLastModificationPersonId(INTERFACE);
        orderPaymentProgress.updateOrderPaymentProgress(evo);
    }

    private JSONObject setJsonObject(String transactionType, JSONObject detail) {
        String requestDate = DateUtil.today("yyyy-MM-dd HH:mm:ss");

        JSONObject data = new JSONObject();
        data.put(ISAKU_MAP_KEY_TIMESTAMP, requestDate);
        data.put(ISAKU_MAP_KEY_CLIENT_ID, domainsConfig.get("isaku.clientID"));
        data.put(ISAKU_MAP_KEY, domainsConfig.get("isaku.key"));
        data.put(ISAKU_MAP_KEY_BRANCH_ID, domainsConfig.get("isaku.BranchID"));
        data.put(ISAKU_MAP_KEY_COUNTER_ID, domainsConfig.get("isaku.CounterID"));
        data.put(ISAKU_MAP_KEY_KASIR, domainsConfig.get("isaku.Kasir"));
        data.put(ISAKU_MAP_KEY_PRODUCT_TYPE, domainsConfig.get("isaku.ProductType"));

        if (ISAKU_TRANSACTION_TYPE_PURCHASE.equals(transactionType)) {
            data.put(ISAKU_MAP_KEY_TRANSACTION_TYPE, ISAKU_TRANSACTION_TYPE_PURCHASE);
        } else if (ISAKU_TRANSACTION_TYPE_REVERSAL_PURCHASE.equals(transactionType)) {
            data.put(ISAKU_MAP_KEY_TRANSACTION_TYPE, ISAKU_TRANSACTION_TYPE_REVERSAL_PURCHASE);
        } else if (ISAKU_TRANSACTION_TYPE_REFUND.equals(transactionType)){
            data.put(ISAKU_MAP_KEY_TRANSACTION_TYPE, ISAKU_TRANSACTION_TYPE_REFUND);
        }

        data.put(ISAKU_MAP_KEY_DETAIL, detail);
        data.put(ISAKU_MAP_KEY_TIMEOUT, "");
        data.put(ISAKU_MAP_KEY_VERSI_PROGRAM, "");
        data.put(ISAKU_MAP_KEY_RESPONSE_CODE, "");
        data.put(ISAKU_MAP_KEY_RESPONSE_DETAIL, "");

        return data;
    }

    private JSONObject sendHttp(String urlString, List<NameValuePair> paramList) throws Exception {
        StringBuilder buf = new StringBuilder();

        URL url = new URL(urlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestProperty(CONTENT_TYPE, FORM_URLENCODED);
        con.setConnectTimeout(
                OrderConstants.NUMBER_THREE * OrderConstants.NUMBER_HUNDRED * OrderConstants.NUMBER_HUNDRED);
        con.setReadTimeout(OrderConstants.NUMBER_THREE * OrderConstants.NUMBER_HUNDRED * OrderConstants.NUMBER_HUNDRED);
        con.setRequestMethod(METHOD_POST);
        con.setDoInput(INPUT_OUTPUT);
        con.setDoOutput(INPUT_OUTPUT);

        OutputStream os = con.getOutputStream();
        BufferedWriter dos = new BufferedWriter(new OutputStreamWriter(os, UTF8));
        dos.write(getQuery(paramList));
        dos.flush();
        dos.close();
        os.close();

        con.connect();

        BufferedReader br;

        if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
            br = new BufferedReader(new InputStreamReader(con.getInputStream(), Constants.DEFAULT_CHARSET));
        } else {
            br = new BufferedReader(new InputStreamReader(con.getErrorStream(), Constants.DEFAULT_CHARSET));
        }

        int c;
        while ((c = br.read()) != -1) {
            buf.append((char) c);
        }
        String returnStr = buf.toString();

        LOGGER.info("interface - e-isaku - returnSendHttp " + returnStr);

        br.close();

        return JSONObject.fromObject(returnStr);
    }

    private String getQuery(List<NameValuePair> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (NameValuePair pair : params) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }

            if (pair.getName() != null && pair.getValue() != null) {
                result.append(URLEncoder.encode(pair.getName(), UTF8));
                result.append("=");
                result.append(URLEncoder.encode(pair.getValue(), UTF8));

                LOGGER.debug("<input type='hidden' name='" + URLEncoder.encode(pair.getName(), UTF8) + "' value='"
                        + URLEncoder.encode(pair.getValue(), UTF8) + "'/>");
            } else {
                result.append(pair.getName());
                result.append("=");
                result.append(pair.getValue());

                LOGGER.debug("<input type='hidden' name='" + pair.getName() + "' value='" + pair.getValue() + "'/>");
            }
        }

        return result.toString();
    }

    private String executeReversalPurchase(ISakuRequestRVO rvo) {
        LOGGER.info("interface - executeReversalPurchase - e-isaku - start");

        //        int count = 0;

        rvo.setTransactionId(transactionId);
        rvo.setGrossAmount(rvo.getGrossAmount());
        rvo.setPgCompanyCode(OrderConstants.PG_COMPANY_CODE_ISAKU);
        rvo.setPaymentMethodDetail(OrderConstants.PAYMENT_DETAIL_METHOD_CODE_ISAKU);
        rvo.setOrderNumber(rvo.getOrderNumber());
        rvo.setChannelId(rvo.getChannelId());
        rvo.setToken(rvo.getToken());

        JSONObject returnJson = null;

        JSONObject detail = new JSONObject();
        detail.put(ISAKU_MAP_KEY_DETAIL_TRANSACTION_ID, transactionId);
        detail.put(ISAKU_MAP_KEY_DETAIL_TRANSACTION_DATE, transactionDate);

        JSONObject header = setJsonObject(ISAKU_TRANSACTION_TYPE_REVERSAL_PURCHASE, detail);

        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("data", header.toString()));

        rvo.setCustomField1(ISAKU_TRANSACTION_TYPE_REVERSAL_PURCHASE);
        rvo.setCustomField2(detail.toString());
        rvo.setCustomField3(STATUS_TIMEOUT);

        insertInterfacePaymentRequest(rvo);
        //        if (count == 0) {
        try {
            LOGGER.info("interface - e-isaku - executeReversalPurchaseReq " + urlParameters);
            returnJson = sendHttp(sendUrl, urlParameters);
            LOGGER.info("interface - e-isaku - executeReversalPurchaseRes " + returnJson);

            rvo.setCustomField2(returnJson.getString(ISAKU_MAP_KEY_DETAIL));
            rvo.setPgPaymentMessage(returnJson.getString(ISAKU_MAP_KEY_RESPONSE_DETAIL));
            rvo.setPgPaymentStatus(returnJson.getString(ISAKU_MAP_KEY_RESPONSE_CODE));
            //                rvo.setCustomField3();

            insertInterfacePaymentReceive(rvo, true);
            //                count++;
        } catch (Exception e) {
            LOGGER.error("interface - executeReversalPurchase - e-isaku - error : [" + e.getCause() + "]");
            LOGGER.info("interface - executeReversalPurchase - e-isaku - error " + e);

            return STATUS_FAIL;
        }
        //        }

        return "" + returnJson.getString(ISAKU_MAP_KEY_RESPONSE_CODE);
    }

    private static Map<String, Object> convertJSONstringToMap(String json) {
        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> retMap = new HashMap<String, Object>();
        try {
            retMap = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new RuntimeFailException("Fail get data from MIDTRANS. {}", e);
        }

        return retMap;
    }

    //    private String singleObjectToString(Object obj) {
    //        String returnString = "";
    //        if(obj != null) {
    //            returnString = obj.toString();
    //        }
    //        return returnString;
    //    }

    //------------------------------------CLAIM------------------------------------------

    public CancellationRequestRVO executeRefund(OrderFormSVO svo) {
        CancellationRequestRVO rvo = new CancellationRequestRVO();
        PaymentInformationEVO paymentInformationEVO = new PaymentInformationEVO();

        String generateTrxDate = DateUtil.today("YYMMddHHmmssSSSSS");
        //        String transactionDate = DateUtil.today("YYYYMMddHHmmss");
        String transactionId = ISAKU_STORE_CODE + generateTrxDate;

        if (svo.getPaymentSVO().getPaymentInformationEVO() == null) {
            paymentInformationEVO.setPgChannelId(svo.getPaymentSVO().getPgChannelId());
            paymentInformationEVO.setPaymentNumber(svo.getPaymentSVO().getPaymentNumber());
            paymentInformationEVO.setOrderNumber(svo.getPaymentSVO().getOrderNumber());
            paymentInformationEVO.setPaymentAmount(svo.getPaymentSVO().getTotalPaymentAmount());
            paymentInformationEVO.setPgCompanyPaymentNumber(null);
        } else {
            paymentInformationEVO = svo.getPaymentSVO().getPaymentInformationEVO();
        }

        ISakuPaymentRefundRequestEVO getISakuPurchase = interfaceService.getTransactionIdISaku(paymentInformationEVO);

        JSONObject detail = new JSONObject();

        detail.put(ISAKU_TRANSACTION_PURCHASE_ID, getISakuPurchase.getTransactionId());
        detail.put(ISAKU_TRANSACTION_ID, transactionId);
        detail.put(ISAKU_DEPOSITE_TYPE, ISAKU_DEPOSITE_TYPE_VALUE);
        //        detail.put(ISAKU_MAP_KEY_DETAIL_TOKEN, "111111111");
        //        detail.put(ISAKU_MAP_KEY_DETAIL_NO_HP, "");
        detail.put(ISAKU_MAP_KEY_DETAIL_NOMINAL, getISakuPurchase.getGrossAmount());
        detail.put(ISAKU_PATNER_CODE, ISAKU_PATNER_CODE_VALUE);
        detail.put(ISAKU_CASHIN_CODE, ISAKU_CASHIN_CODE_VALUE);

        JSONObject requestJson = setJsonObject(ISAKU_TRANSACTION_TYPE_REFUND, detail);

        String sendData = JSONObject.fromObject(requestJson).toString();
        LOGGER.info("=== transaction isaku sendData ===" +sendData);
        String sendUrl = domainsConfig.get("isaku.url");
        LOGGER.info("=== transaction isaku API URL ===" + sendUrl);
        
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("data", sendData));

        Map<String, Object> parameter = setRefundParameter(paymentInformationEVO);

        ISakuPaymentRefundRequestEVO iSakuPaymentRefundRequestEVO = insertInterfaceRefund(
                paymentInformationEVO.getOrderNumber(), getISakuPurchase.getPaymentMthdDtl(), parameter, detail);

        try {
            requestJson = sendHttp(sendUrl, urlParameters);
        } catch (Exception e) {
            throw new RuntimeFailException("Fail connect ISAKU. : {}", e);
        }

        Map<String, Object> retMap = new HashMap<>();
        try {
            retMap = convertJSONstringToMap(JSONObject.fromObject(requestJson).toString());
        } catch (Exception e) {
            throw new RuntimeFailException("Fail get data from ISAKU. {}", e);
        }

        updateInterfaceRefund(paymentInformationEVO.getOrderNumber(),
                iSakuPaymentRefundRequestEVO.getInterfaceDataNumber(), retMap);

        rvo.setPgCompanyPaymentNumber(singleObjectToString(retMap.get("transaction_id")) + "|"
                + singleObjectToString(retMap.get("refund_chargeback_id")));
        rvo.setResultCode(singleObjectToString(retMap.get("RespCode")));
        rvo.setResultMessage(singleObjectToString(retMap.get("RespDetail")));
        LOGGER.debug("=== transaction isaku response ===", retMap);

        return rvo;
    }

    protected ISakuPaymentRefundRequestEVO insertInterfaceRefund(String orderNo, String paymentMthdDtl,
            Map<String, Object> parameter, JSONObject detail) {
        ISakuPaymentRefundRequestEVO evo = new ISakuPaymentRefundRequestEVO();

        String id = INTERFACE;
        evo.setInterfaceNumber("EC_OP_I0041");
        evo.setBusinessModuleCode("OP");
        evo.setInterfaceTypeCode(ConstCode.IF_TYCD_REALTIME);
        evo.setIfSendingReceivingTyCd(ConstCode.IF_SNR_TYCD_SEND);
        evo.setInterfaceProgressStatusCode(ConstCode.IF_PROG_STCD_PROGRESS);
        evo.setInterfaceExecutorId(id);
        evo.setPaymentMthdDtl(paymentMthdDtl);
        evo.setInterfaceExecutionCountNumber(1);

        evo.setIfExeResultMsgCtt(JSONObject.fromObject(detail).toString());
        evo.setFirstRegistrationPersonId(id);
        evo.setLastModificationPersonId(id);
        evo.setCustField1("REFUND");
        evo.setCustField2(JSONObject.fromObject(detail).toString());
        evo.setCustField3("REFUND_ID");
        evo.setOrderNo(orderNo);

        LOGGER.debug("INSERT REFUND: {}", evo);
        interfaceService.insertISakuPaymentRefundRequest(evo);

        return evo;
    }

    protected void updateInterfaceRefund(String orderNo, String interfaceDataNumber, Map<String, Object> rvo) {
        ISakuPaymentRefundRequestEVO evo = new ISakuPaymentRefundRequestEVO();
        String id = INTERFACE;
        evo.setInterfaceNumber("EC_OP_ISAKU");
        evo.setInterfaceDataNumber(interfaceDataNumber);

        evo.setBusinessModuleCode("CL");
        evo.setInterfaceTypeCode(ConstCode.IF_TYCD_REALTIME);
        evo.setIfSendingReceivingTyCd(ConstCode.IF_SNR_TYCD_RCV);

        if ("00".equals(singleObjectToString(rvo.get("RespCode")))) {
            evo.setInterfaceProgressStatusCode(ConstCode.IF_PROG_STCD_SUCCESS);
        } else {
            evo.setInterfaceProgressStatusCode(ConstCode.IF_PROG_STCD_FAIL);
        }
        evo.setIfExeResultMsgCtt(singleObjectToString(rvo.get("status_message")));
        evo.setLastModificationPersonId(id);
        evo.setInterfaceExecutionCountNumber(1);
        evo.setTransactionId(singleObjectToString(rvo.get("transaction_id")));
        evo.setGrossAmount(singleObjectToString(rvo.get("gross_amount")));
        evo.setPaymentType(singleObjectToString(rvo.get("payment_type")));
        evo.setTransactionTime(singleObjectToString(rvo.get("transaction_time")));
        evo.setTransactionStatus(singleObjectToString(rvo.get("transaction_status")));
        //        JSONObject respondJson = JSONObject.fromObject(rvo.get("Detail"));
        evo.setCustField2(singleObjectToString(rvo.get("Detail")));
        evo.setStatusMessage(singleObjectToString(rvo.get("status_message")));
        //        evo.setCustField2(JSONObject.fromObject(respondJson).toString());
        evo.setFirstRegistrationPersonId(id);
        evo.setLastModificationPersonId(id);
        evo.setCustField1("REFUND");
        evo.setCustField3("REFUND_ID");
        evo.setOrderNo(orderNo);

        LOGGER.debug("UPDATE REFUND: {}", evo);
        interfaceService.insertISakuPaymentRespondRefund(evo);
    }

    private Map<String, Object> setRefundParameter(PaymentInformationEVO evo) {
        Map<String, Object> parameters = new HashMap<>();
        String reason = "Full Cancellation";

        if (evo.getPartialCancelYnFlag() != null && evo.getPartialCancelYnFlag().equals("Y")) {
            reason = "Partial Refund";
        }

        parameters.put("amount", evo.getPaymentAmount());
        parameters.put("reason", reason);

        return parameters;
    }

    private String singleObjectToString(Object obj) {
        String returnString = "";
        if (obj != null) {
            returnString = obj.toString();
        }
        return returnString;
    }
}
