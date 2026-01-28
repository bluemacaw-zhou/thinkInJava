```plantuml
@startuml 客户端服务端交互与三层架构
!theme plain
skinparam backgroundColor #FFFFFF
skinparam handwritten false
skinparam defaultFontSize 13

title 客户端-服务端交互模型 与 客户端三层处理架构

|服务端|
start

:服务端职责;
note right
  <b>服务端核心职责:</b>

  1. 记录同步状态:
     - DeviceSubscription.last_sync_version
     - 每个设备的消息同步进度

  2. 记录红点状态:
     - UserSubscription.last_read_version
     - 用户的消息已读进度

  3. 推送变化:
     - 推送有新消息的频道列表
     - 推送频道对应的消息数据
     - 携带 channel_version

  4. 提供数据源:
     - 客户端请求时提供消息数据
     - Redis 缓存 (最新150条)
     - MongoDB (历史消息)

  <b>服务端不负责:</b>
  ❌ 不构建完整消息副本
  ❌ 不处理消息顺序问题
  ❌ 不计算实时红点
end note

:推送消息/接收请求;

|客户端|

:接收服务端数据;
note right
  <b>接收到的数据:</b>
  - WebSocket 推送消息
  - 登录后批量同步
  - 主动拉取消息

  <b>数据特点:</b>
  - 可能乱序
  - 可能有 gap
  - 需要客户端处理
end note

:进入客户端三层架构;
note right
  <b>客户端三层处理架构:</b>

  Layer 1: 预处理层 (PreProcess Layer)
  - 滑动窗口缓冲区
  - 处理消息乱序和 gap
  - 等待缺失消息到达

  Layer 2: 消息处理层 (Process Layer)
  - 本地消息副本存储
  - 统一的消息消费逻辑
  - 红点计算、UI更新

  Layer 3: 后处理层 (AfterProcess Layer)
  - 待上报会话队列
  - 同步进度上报
  - 已读状态上报
end note

|Layer 1: 预处理层|

:步骤1: 检查消息连续性;
note right
  <b>连续性检测:</b>

  推送 seq == client.channel_version + 1
  → 连续 ✅

  推送 seq > client.channel_version + 1
  → 不连续，有 gap ❌
end note

if (消息 seq 连续?) then (是)

    :标记消息可直接处理;
    note right
      <b>消息连续:</b>
      seq == client.channel_version + 1
      可以跳过滑动窗口
      直接进入消息处理层
    end note

  else (否，有 gap)

    |Layer 1: 预处理层|

    :消息落库到滑动窗口;
    note right
      <b>检测到 gap，缓冲处理:</b>

      gap = 推送 seq - client.channel_version - 1
      缺失消息: [client.channel_version+1, 推送seq-1]

      <b>示例:</b>
      本地 version = 100
      推送 seq = 105
      gap = 4
      缺失: [101, 102, 103, 104]

      <b>滑动窗口结构:</b>
      sliding_window[channel_id] = {
        min_seq: 101,
        max_seq: 105,
        missing: [101,102,103,104],
        pending_messages: {105: {...}},
        wait_start_time: now(),
        timeout: 5000
      }
    end note

    :启动5秒超时检测;

    if (5秒内缺失消息到达?) then (是)

      :填充滑动窗口;
      note right
        <b>缺失消息到达:</b>
        1. 消息落库到滑动窗口
        2. 从 missing 列表移除
        3. 检查窗口是否连续
      end note

      if (窗口头部连续?) then (是)
        :窗口头部稳定;
        note right
          检测到窗口头部已连续
          准备送到消息处理层
        end note
      else (否，头部仍有gap)
        :继续等待;
        note right
          窗口头部仍不连续
          等待更多消息到达
        end note
      endif

    else (否，5秒超时)

      :主动拉取缺失消息;
      note right
        <b>超时拉取策略:</b>

        请求参数:
        {
          channel_id: "AB",
          since_version: 100,
          until_version: 104
        }

        <b>说明:</b>
        精确查询缺失的消息区间
      end note

      |服务端|
      :查询并返回缺失消息;

      |Layer 1: 预处理层|

      if (拉取成功?) then (是)

        :填充滑动窗口;
        :窗口头部稳定;
        note right
          超时拉取后窗口补齐
          准备送到消息处理层
        end note

      else (否,拉取失败)

        :标记网络异常;
        note right
          <b>拉取失败处理:</b>

          拉取失败意味着网络出现问题

          <b>处理策略:</b>
          1. 放弃当前窗口中的缺失消息
          2. 将窗口中已收到的最新消息展示
          3. 计算并更新红点
          4. 等待心跳或重新登录唤醒
          5. 重新请求同步流程
        end note

        :取出窗口中最新消息;
        note right
          <b>降级处理:</b>

          窗口: [gap, gap, gap, 105]
          取出: [105] (只取最新收到的消息)

          <b>说明:</b>
          虽然有gap,但优先展示最新消息
          避免用户长时间看不到新消息
        end note

        :送入消息处理层;
        note right
          <b>特殊标记:</b>
          message.has_gap = true

          提示用户消息可能不连续
        end note

        :等待网络恢复;
        note right
          <b>恢复时机:</b>
          1. 心跳检测网络恢复
          2. 用户重新登录

          <b>恢复操作:</b>
          重新发起订阅同步流程
          补全缺失的消息
        end note

      endif

    endif

    :从滑动窗口取出连续消息;
    note right
      <b>取出规则:</b>
      从窗口头部取出所有连续的消息
      按 seq 顺序排列
      准备送入消息处理层

      <b>示例:</b>
      窗口: [101, 102, 103, 106, 107]
      取出: [101, 102, 103]
      保留: [106, 107] (仍有gap)
    end note

  endif

|Layer 2: 消息处理层|

:接收消息队列;
note right
  <b>接收到的消息:</b>
  - 连续消息: 直接处理单条
  - 窗口消息: 批量处理多条

  <b>统一处理:</b>
  无论来源，都按 seq 顺序处理
end note

repeat
  :取出下一条消息;

  :写入本地消息副本;

  :执行统一消费逻辑;
  note right
    <b>统一消费逻辑:</b>

    1. 更新版本号:
       client.channel_version++
       client.lastSyncVersion++

    2. 计算红点:
       - 正常消息: unread_count += 1
       - 撤回消息: 重新计算

    3. 更新 UI:
       - 显示消息
       - 更新红点

    4. 触发业务逻辑:
       - 通知
       - 音效
  end note

repeat while (还有消息?) is (是)
-> 否;

:消息处理完成;
note right
  <b>本地消息副本已更新:</b>

  ✅ client.channel_version 已更新
  ✅ client.lastSyncVersion 已更新
  ✅ 红点已计算
  ✅ UI 已更新

  <b>需要上报服务端</b>
end note

|Layer 3: 后处理层|

:进入后处理层;

:检查该频道滑动窗口状态;
note right
  <b>检查规则:</b>
  检查 sliding_window[channel_id] 是否存在

  如果存在滑动窗口:
  说明该频道还有消息在等待
  需要将当前变更归并到待上报队列
  等待窗口稳定后统一上报

  如果不存在滑动窗口:
  说明该频道消息已稳定
  可以立即上报
end note

if (该频道有滑动窗口?) then (是)

  :归并到待上报队列;
  note right
    <b>归并逻辑:</b>

    pending_reports[channel_id] = {
      channel_id: "AB",
      channel_version: 105,
      last_read_version: 100,
      last_sync_version: 105,
      report_type: "sync_only",
      retry_count: 0,
      last_attempt: now(),
      waiting_for_window: true  // 标记等待窗口
    }

    <b>等待时机:</b>
    - 当滑动窗口清空时
    - 窗口中的消息全部处理完成
    - 再触发统一上报
  end note

  :等待窗口稳定;

else (否，无滑动窗口)

  :构建上报数据;
  note right
    <b>准备上报:</b>

    {
      channel_id: "AB",
      channel_version: 105,
      last_read_version: 100,
      last_sync_version: 105,
      report_type: "sync_only"
    }
  end note

  :POST /api/subscription/report;

  |服务端|
  :更新 DeviceSubscription;
  note right
    <b>变更前:</b>
    last_sync_version: 100

    <b>变更后:</b>
    last_sync_version: 105
  end note

  if (上报成功?) then (是)

    :返回 200 OK;

    |Layer 3: 后处理层|
    :确认上报成功;

  else (否)

    |服务端|
    :返回失败;

    |Layer 3: 后处理层|
    :加入待上报队列;
    note right
      <b>失败处理:</b>

      pending_reports[channel_id] = {
        channel_id: "AB",
        channel_version: 105,
        last_read_version: 100,
        last_sync_version: 105,
        report_type: "sync_only",
        retry_count: 0,
        last_attempt: now(),
        waiting_for_window: false  // 非窗口等待
      }

      <b>重试策略:</b>
      - 延迟 2 秒后重试
      - 最多重试 3 次
      - 持久化到本地数据库

      <b>触发时机:</b>
      - 定时重试
      - 下次登录
    end note

    :延迟2秒后重试;

  endif

endif

|客户端|

:客户端处理完成;
note right
  <b>客户端已完成全部职责:</b>

  ✅ 构建了本地完整消息副本
  ✅ 处理了消息乱序和 gap
  ✅ 计算了准确的红点
  ✅ 更新了 UI
  ✅ 上报了同步状态

  <b>客户端的核心价值:</b>
  基于服务端提供的数据
  构建本地正确完整的消息副本
end note

stop

@enduml
```

---

## 架构说明文档

### 一、服务端职责

**核心职责：**
1. **记录同步状态**：`DeviceSubscription.last_sync_version` - 每个设备的消息同步进度
2. **记录红点状态**：`UserSubscription.last_read_version` - 用户的消息已读进度
3. **推送变化**：推送有新消息的频道列表和消息数据，携带 `channel_version`
4. **提供数据源**：Redis 缓存（最新150条）、MongoDB（历史消息）

**服务端不负责：**
- ❌ 不构建完整消息副本
- ❌ 不处理消息顺序问题
- ❌ 不计算实时红点

---

### 二、客户端职责

**核心职责：**
根据服务端提供的数据，构建本地正确完整的消息副本。

---

### 三、客户端三层处理架构

#### **Layer 1: 预处理层 (PreProcess Layer) - 滑动窗口**

**职责：**
- 处理消息乱序和 gap
- 缓冲不连续的消息
- 等待缺失消息到达
- 超时主动拉取

**数据结构：**
```javascript
sliding_window[channel_id] = {
  min_seq: 101,                  // 缺失的最小 seq
  max_seq: 105,                  // 当前最大 seq
  missing: [101, 102, 103, 104], // 缺失列表
  pending_messages: {
    105: {...}                   // 已收到的消息
  },
  wait_start_time: now(),
  timeout: 5000                  // 5秒超时
}
```

**处理流程：**
1. 检查消息连续性
2. 连续 → 标记可直接处理
3. 不连续 → 进入滑动窗口，等待/拉取缺失消息
4. 窗口头部稳定 → 取出所有连续消息
5. 统一送入 Layer 2 处理

---

#### **Layer 2: 消息处理层 (Process Layer) - 本地消息副本**

**职责：**
- 存储本地消息副本
- 执行统一的消息消费逻辑
- 计算红点
- 更新 UI

**统一消费逻辑：**
```javascript
// 无论消息来自推送还是滑动窗口，都执行相同逻辑
function processMessage(message) {
  // 1. 更新版本号
  client.channel_version++;
  client.lastSyncVersion++;

  // 2. 计算红点
  if (message.msg_type === 'normal') {
    unread_count += 1;
  } else if (message.msg_type === 'recall') {
    unread_count = recalculateFromDB();
  }

  // 3. 更新 UI
  displayMessage(message);
  updateRedDot(unread_count);

  // 4. 触发业务逻辑
  triggerNotification();
}
```

**关键特性：**
- 接收单条或批量消息队列
- 消息连续 → 单条直接处理
- 窗口稳定 → 批量取出连续消息处理
- 所有消息都经过统一消费逻辑
- 无重复处理分支

---

#### **Layer 3: 后处理层 (AfterProcess Layer) - 待上报队列**

**职责：**
- 管理待上报会话队列
- 上报同步进度到服务端
- 上报已读状态到服务端
- 处理上报失败重试

**数据结构：**
```javascript
pending_reports[channel_id] = {
  channel_id: "AB",
  channel_version: 105,
  last_read_version: 100,
  last_sync_version: 105,
  report_type: "sync_only",
  retry_count: 0,
  last_attempt: now(),
  waiting_for_window: false  // 是否等待滑动窗口稳定
}
```

**上报策略：**
1. **检查滑动窗口**：
   - 有窗口 → 归并到待上报队列，等待窗口稳定
   - 无窗口 → 立即上报

2. **归并上报**：
   - 同一频道的多次变更归并为一次上报
   - 避免频繁上报

3. **失败重试**：
   - 延迟2秒，最多3次
   - 持久化到本地数据库
   - 触发时机：定时重试、下次登录

---

### 四、数据流转路径

#### **路径1：消息连续（快速路径）**
```
服务端推送 → Layer 1 (检查连续性，标记可处理) → Layer 2 (单条处理)
→ Layer 3 (检查窗口，无窗口则上报)
```

#### **路径2：消息不连续（滑动窗口路径）**
```
服务端推送 → Layer 1 (进入滑动窗口) → 等待/拉取缺失消息
→ Layer 1 (窗口稳定，取出连续消息) → Layer 2 (批量处理)
→ Layer 3 (检查窗口，有窗口则归并等待，无窗口则上报)
```

#### **路径3：窗口归并上报**
```
Layer 2 处理完成 → Layer 3 (发现有窗口，归并到待上报队列)
→ 等待窗口清空 → Layer 3 (窗口稳定，统一上报)
```

---

### 五、关键设计原则

1. **服务端提供数据，客户端构建副本**
   - 职责清晰分离
   - 客户端处理复杂度

2. **三层架构，单向流动**
   - Layer 1 → Layer 2 → Layer 3
   - 逐层推进，不回退

3. **统一消费逻辑**
   - Layer 2 统一处理所有消息
   - 无论来源（直接/窗口），都经过相同处理
   - 无重复处理分支
   - 保证一致性

4. **消息处理后再上报**
   - Layer 2 处理完成后进入 Layer 3
   - 检查滑动窗口状态决定上报时机
   - 确保状态最终一致

5. **滑动窗口稳定后送出**
   - 窗口头部连续才稳定
   - 取出所有连续消息统一送入 Layer 2
   - 保证消息顺序

6. **归并上报机制**
   - 检查频道是否有滑动窗口
   - 有窗口 → 归并等待
   - 无窗口 → 立即上报
   - 避免频繁上报，减少网络开销

7. **5秒超时保护**
   - 防止无限等待
   - 主动拉取补齐
   - 拉取失败时降级处理：展示最新消息并等待网络恢复
