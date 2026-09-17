package li.gkd.app.ui.share

import li.gkd.app.data.subscription.SubscriptionResult

val SubscriptionResult.message: String?
    get() = when (this) {
        SubscriptionResult.Busy -> "正在处理订阅，请稍后重试"
        is SubscriptionResult.Success -> when (kind) {
            SubscriptionResult.SuccessKind.None -> null
            SubscriptionResult.SuccessKind.Deleted -> "删除成功"
            SubscriptionResult.SuccessKind.Added -> "成功添加订阅"
            SubscriptionResult.SuccessKind.Modified -> "成功修改订阅"
            SubscriptionResult.SuccessKind.Refreshed -> {
                if (count > 0) "更新 $count 条订阅" else "暂无更新"
            }
        }

        is SubscriptionResult.Failure -> when (reason) {
            SubscriptionResult.FailureReason.DeleteData -> detailMessage("删除订阅数据失败", detail)
            SubscriptionResult.FailureReason.DeleteFile ->
                detailMessage("删除订阅文件失败，已取消删除", detail)
            SubscriptionResult.FailureReason.DuplicateUrl -> "已有相同链接订阅"
            SubscriptionResult.FailureReason.Download -> detailMessage("下载订阅文件失败", detail)
            SubscriptionResult.FailureReason.Parse -> detailMessage("解析订阅文件失败", detail)
            SubscriptionResult.FailureReason.AlreadyExists -> "订阅已存在"
            SubscriptionResult.FailureReason.IdMismatch -> "订阅id不对应"
            SubscriptionResult.FailureReason.InvalidId -> "订阅id不可为$detail\n负数id为内部使用"
            SubscriptionResult.FailureReason.Save -> detailMessage("保存订阅文件失败", detail)
            SubscriptionResult.FailureReason.NetworkUnavailable -> "网络不可用"
        }
    }

private fun detailMessage(message: String, detail: String?): String =
    if (detail.isNullOrBlank()) message else "$message\n$detail"
