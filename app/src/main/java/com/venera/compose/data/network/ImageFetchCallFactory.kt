package com.venera.compose.data.network

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 图片专用的 [Call.Factory]：把 [okHttpClient] 原样包一层，只多干一件事 ——
 * 给每一笔请求盖上 [ImageFetchTag]。
 *
 * 为什么需要这层包装：普通图片 2026-09-29 起改由 Coil 自带的 `OkHttpNetworkFetcherFactory`
 * 取流（流式落盘、文件源解码，理由见 [ImagePipelinePolicy.needsBytePipeline]），
 * 而 Coil 构造 `okhttp3.Request` 时只填 url/method/headers，`Options.extras` **不会**
 * 映射成 OkHttp tag。少了这个标记，[HostCircuitBreakerInterceptor] 会把图片超时算进
 * 域名熔断（两笔就把整站封面拉黑 60 秒），[CloudflareBypassInterceptor] 还会在取图线程上
 * 弹人机验证页 —— 两条都是画廊明确不许的行为。
 *
 * 防盗链头与 UA 不用在这里补：它们本来就是共享客户端上的拦截器
 * （[VeneraNetworkClient] 的第 1、5 条），任何从该客户端出去的请求都吃得到。
 */
class ImageFetchCallFactory(
    private val okHttpClient: OkHttpClient,
) : Call.Factory {

    override fun newCall(request: Request): Call =
        okHttpClient.newCall(request.withImageFetchTag())
}

/** 补上图片取流标记；已有标记（自定义 fetcher 那条路）时原样返回。 */
fun Request.withImageFetchTag(): Request =
    if (tag(ImageFetchTag::class.java) != null) this
    else newBuilder().tag(ImageFetchTag::class.java, ImageFetchTag()).build()
