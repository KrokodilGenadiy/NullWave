// EXPECT_DIAGNOSTIC: INVISIBLE_REFERENCE
package contract

import com.zaus.nullwave.core.mvi.dsl.Source

private fun bypass(source: Source<ContractState>) = source.refresh { this }
