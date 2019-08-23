/* Copyright PT. Indo Lotte Makmur, Inc - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited Proprietary and confidential copying of this file, via any medium is strictly prohibited Proprietary and confidential
 * 
 * Written by ECM project team
 */
package com.indolotte.front.biz.op.service.payment;

import javax.ws.rs.core.Response;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.indolotte.OrderConstants;
import com.indolotte.base.exception.OrderVerificationException;
import com.indolotte.base.exception.RuntimeFailException;
import com.indolotte.base.mvc.ApiClientProxy;
import com.indolotte.biz.op.api.ISakuApi;
import com.indolotte.entity.api.op.ISakuRequestSVO;
import com.indolotte.front.entity.api.op.order.OrderFormSVO;
import com.indolotte.front.entity.biz.op.payment.ISakuEVO;

@Service
@Lazy
public class ISakuPaymentGateway extends PaymentGatewayFoAbstract {
    protected static final String UTF8 = "UTF-8";

    @Override
    public void executeApprove(OrderFormSVO orderFormSVO) {
        LOGGER.info("front - executeApprovePG - e-isaku");
        ISakuEVO iSakuEVO = new ISakuEVO();
        
        try {
            ISakuRequestSVO svo = new ISakuRequestSVO();
            svo.setOrderNumber(orderFormSVO.getOrderNumber());
            
            String domainUrl = domainsConfig.get("apiClientProxy.apiServerUrl.IF");
            ApiClientProxy client = applicationContext.getBean(ApiClientProxy.class);
            ISakuApi remoteApi = client.create(domainUrl, ISakuApi.class, svo);
            String insertMessage = ""; 
            
            updateOrderPaymentProgress(orderFormSVO.getPaymentSVO(), OrderConstants.ORDER_PAYMENT_PROGRESS_STATUS_CODE_INTERFACE_START);
            
            Response response = remoteApi.executeApprove(svo);
            
            iSakuEVO = response.readEntity(ISakuEVO.class);
            
            String paymentResultCode = OrderConstants.FAIL_CODE;
            
            if (OrderConstants.SUCCESS_CODE.equals(iSakuEVO.getPgPaymentStatus())) {
                paymentResultCode = OrderConstants.SUCCESS_CODE;
            }
            
            insertMessage = iSakuEVO.getPgPaymentMessage();
            
            orderFormSVO.getPaymentSVO().setPgChannelId(iSakuEVO.getChannelId());
            orderFormSVO.getPaymentSVO().setPgCompanyCode(OrderConstants.PG_COMPANY_CODE_ISAKU);
            orderFormSVO.getPaymentSVO().setPaymentResultCode(paymentResultCode);
            orderFormSVO.getPaymentSVO().setPaymentResultMessage(insertMessage);
//            orderFormSVO.getPaymentSVO().setMidtransEVO(evo);
            
            if(OrderConstants.SUCCESS_CODE.equals(paymentResultCode)) {
                updateOrderPaymentProgress(orderFormSVO.getPaymentSVO(), OrderConstants.ORDER_PAYMENT_PROGRESS_STATUS_CODE_INTERFACE_COMPLETE);
            } else {
                updateOrderPaymentProgress(orderFormSVO.getPaymentSVO(), OrderConstants.ORDER_PAYMENT_PROGRESS_STATUS_CODE_INTERFACE_ERROR);
                throw new OrderVerificationException("Fail - payment - e-isaku: "+ insertMessage);
            }
            
            LOGGER.info("front - executeApprovePG - e-isaku - response " + response);
        } catch (Exception e) {
            LOGGER.error("front - executeApprovePG - e-isaku - error : [" + e.getCause() + "]");
            updateOrderPaymentProgress(orderFormSVO.getPaymentSVO(), OrderConstants.ORDER_PAYMENT_PROGRESS_STATUS_CODE_INTERFACE_ERROR);
            throw new RuntimeFailException("front - executeApprovePG - error : {}", e);
        }
    }

}
