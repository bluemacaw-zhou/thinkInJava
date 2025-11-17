package cn.com.wind.service;

import cn.com.wind.entity.MsgAnalysisData;
import cn.com.wind.entity.MsgData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 消息数据转换服务
 * 将MsgData转换为MsgAnalysisData用于ClickHouse存储
 */
@Slf4j
public class MsgAnalysisDataConverter {

    /**
     * 将MsgData转换为MsgAnalysisData
     *
     * @param msgData 原始消息数据
     * @return 转换后的分析数据
     */
    public static MsgAnalysisData convert(MsgData msgData) {
        if (msgData == null) {
            log.warn("MsgData is null, cannot convert");
            return null;
        }

        try {
            MsgAnalysisData analysisData = new MsgAnalysisData();

            // 复制所有字段
            analysisData.setMsgId(msgData.getMsgId());
            analysisData.setFromId(msgData.getFromId());
            analysisData.setContactId(msgData.getContactId());
            analysisData.setContactType(msgData.getContactType());
            analysisData.setFromCompanyId(msgData.getFromCompanyId());
            analysisData.setFromCompany(msgData.getFromCompany());
            analysisData.setContactCompanyId(msgData.getContactCompanyId());
            analysisData.setContactCompany(msgData.getContactCompany());
            analysisData.setOldMsgId(msgData.getOldMsgId());
            analysisData.setMsgType(msgData.getMsgType());
            analysisData.setMsgTime(msgData.getMsgTime());
            analysisData.setDeleted(msgData.getDeleted());
            analysisData.setStatus(msgData.getStatus());
            analysisData.setContent(msgData.getContent());
            analysisData.setContentVersion(msgData.getContentVersion());
            analysisData.setClientMsgId(msgData.getClientMsgId());
            analysisData.setClientInfo(msgData.getClientInfo());

            // 处理时间字段
            LocalDateTime createTime = msgData.getCreateTime();
            LocalDateTime updateTime = msgData.getUpdateTime();

            // 如果原始数据没有createTime,使用当前时间
            if (createTime == null) {
                createTime = LocalDateTime.now();
            }

            // 如果原始数据没有updateTime,使用createTime
            if (updateTime == null) {
                updateTime = createTime;
            }

            analysisData.setCreateTime(createTime);
            analysisData.setUpdateTime(updateTime);

            log.debug("Successfully converted MsgData to MsgAnalysisData, msgId: {}", msgData.getMsgId());
            return analysisData;

        } catch (Exception e) {
            log.error("Error converting MsgData to MsgAnalysisData, msgId: {}", msgData.getMsgId(), e);
            return null;
        }
    }

    /**
     * 批量转换
     *
     * @param msgDataList 消息数据列表
     * @return 转换后的分析数据列表
     */
    public static List<MsgAnalysisData> convertBatch(List<MsgData> msgDataList) {
        if (msgDataList == null || msgDataList.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return msgDataList.stream()
                .map(MsgAnalysisDataConverter::convert)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
    }
}
