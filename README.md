# BassMemo 🎸 · 贝斯指板记忆训练

专为安卓横屏设计的离线四弦贝斯指板记忆应用（标准 E–A–D–G，0–12 品）。

## 学习方法

1. **锚点学习**：先记空弦、3/5/7/9/12 品；利用 5 品跨弦、12 品重复和八度形状定位。
2. **主动回忆**：看到指定弦与品位，先自己说出音名，再揭晓。只有直接想起、没有依赖推算才标记为记住。
3. **间隔复习**：按 1、3、7、14、30 天逐步安排复习；错误重置，错题提高抽取概率。复习安排是启发式设计，不是经过临床验证的最优间隔。
4. **跨弦找音**：给出一个音名，在四根弦 0–12 品范围找到全部对应位置，最后一次性检查。
5. **循序渐进**：空弦 → E/A 弦自然音 → 四弦自然音 → 全部半音。

## 横屏

应用 Activity 强制横屏；指板根据屏幕可用宽度自动分配品格宽度，空间不足时仍支持横向滑动。

## 构建与安装

进入 [GitHub Actions](https://github.com/f1nKero/Bass-learning/actions/workflows/android.yml)，打开最新成功的 **Build Android APK** 运行，在 **Artifacts** 下载 `BassMemo-debug-apk` ZIP，解压并安装 APK。

源码使用 Kotlin、Jetpack Compose、Gradle 8.9、JDK 17。最低 Android 8.0。当前为 MVP，未接入真实贝斯声音识别或音频播放。

## 参考

- [StudyBass: Memorizing Fretboard Note Names](https://www.studybass.com/lessons/fretboard-notes/memorizing-fretboard-note-names/)
- [TalkingBass: Learn The Fretboard](https://www.talkingbass.net/learn-the-fretboard-how-to-memorize-the-notes-of-the-bass-guitar/)
- [StudyBass: Note Name Memorization Method](https://www.studybass.com/lessons/fretboard-notes/studybass-note-name-memorization-method/)
- [Dunlosky et al. (2013): Effective Learning Techniques](https://www.psychologicalscience.org/publications/journals/pspi/learning-techniques.html)
