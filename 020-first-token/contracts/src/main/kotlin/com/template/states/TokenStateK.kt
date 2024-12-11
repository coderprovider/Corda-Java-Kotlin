package com.template.states

import com.template.contracts.TokenContractK
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(TokenContractK::class)
data class TokenStateK(val issuer: Party, val holder: Party, val quantity: Long) : ContractState {

    override val participants: List<AbstractParty> = listOf(holder)
}
