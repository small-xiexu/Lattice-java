# 借阅 API 接口说明

## 借书
- **Endpoint**: `POST /api/v1/lending/borrow`
- **请求体**: `{"userId": 1001, "bookIds": [101, 102]}`
- **校验**: `userId` 不能为空，`bookIds` 长度 1-5
- **成功响应**: `{"userId": 1001, "borrowedCount": 2}`
- **错误**: 超过借书上限返回 400，提示 "exceeds max books per user: 5"

## 还书
- **Endpoint**: `POST /api/v1/lending/return?lendingId=5001`
- **成功响应**: `{"lendingId": 5001, "returned": true}`

## 罚金计算
- **Endpoint**: `POST /api/v1/fine/calculate`
- **请求体**: `{"lendingId": 5001, "returnDate": "2026-07-15"}`
- **响应**: `{"lendingId": 5001, "overdueDays": 3, "fineAmount": 3.0}`

## 信用分查询
- **Endpoint**: `GET /api/v1/credit/{userId}`
- **响应**: 当前信用分（整数）
