package io.github.vkeiwon.junitgen.plugin.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import io.github.vkeiwon.junitgen.core.AssertionStyle
import io.github.vkeiwon.junitgen.core.JUnitVersion

/** Settings → Tools → JUnit Test Generator */
class JUnitGenConfigurable : BoundConfigurable("JUnit Test Generator") {

    override fun createPanel(): DialogPanel {
        val options = JUnitGenSettings.getInstance().state
        return panel {
            group("테스트 프레임워크") {
                row("JUnit 버전:") {
                    comboBox(JUnitVersion.entries)
                        .bindItem({ options.junitVersion }, { options.junitVersion = it ?: JUnitVersion.JUNIT5 })
                }
                row("Assertion 스타일:") {
                    comboBox(AssertionStyle.entries)
                        .bindItem({ options.assertionStyle }, { options.assertionStyle = it ?: AssertionStyle.JUNIT })
                }
                row {
                    checkBox("Mockito 사용 (협력 객체 @Mock, when/thenReturn, verify 생성)")
                        .bindSelected(options::useMockito)
                }
            }
            group("생성 옵션") {
                row {
                    checkBox("@DisplayName 생성 (JUnit 5)").bindSelected(options::generateDisplayNames)
                }
                row {
                    checkBox("throws 절의 예외마다 예외 테스트 골격 생성 (@Disabled 상태)").bindSelected(options::generateExceptionTests)
                }
                row {
                    checkBox("단순 getter/setter 는 테스트하지 않음").bindSelected(options::skipAccessors)
                }
                row("테스트 클래스 접미사:") {
                    textField().bindText(options::testClassSuffix)
                }
            }
        }
    }
}
