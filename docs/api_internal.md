# `api` 与 `internal`

与常识不同，这里的 `api` 和 `internal` 并不完全是其英语的语义。

故规定：

`api` 的语义为：外部、基础、通用。

类似 WirelessUser HUD Skill 这种感觉。

`internal` 的语义为：内部、实现、具体。

类似 AcademyCraftClientData Electromaster Teleport 这种感觉。

两者是单向引用的，即 `api` 被 `internal` 使用；`internal` 被 `api` 使用是错误的。

类根据语义进行放置。

对于外部两者均可使用。

两者均不保证更新后二进制兼容。
