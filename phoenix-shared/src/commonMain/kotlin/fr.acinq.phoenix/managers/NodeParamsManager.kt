/*
 * Copyright 2021 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.managers

import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.NodeParams
import fr.acinq.lightning.NodeUri
import fr.acinq.lightning.logging.LoggerFactory
import fr.acinq.lightning.payment.LiquidityPolicy
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.sat
import fr.acinq.phoenix.PhoenixBusiness
import fr.acinq.phoenix.shared.BuildVersions
import fr.acinq.lightning.logging.info
import fr.acinq.lightning.wire.OfferTypes
import fr.acinq.phoenix.defaultScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


class NodeParamsManager(
    loggerFactory: LoggerFactory,
    chain: Chain,
    walletManager: WalletManager,
    appConfigurationManager: AppConfigurationManager,
) : CoroutineScope by defaultScope() {

    constructor(business: PhoenixBusiness): this(
        loggerFactory = business.loggerFactory,
        chain = business.chain,
        walletManager = business.walletManager,
        appConfigurationManager = business.appConfigurationManager,
    )

    private val log = loggerFactory.newLogger(this::class)

    private val _nodeParams = MutableStateFlow<NodeParams?>(null)
    val nodeParams: StateFlow<NodeParams?> = _nodeParams

    init {
        launch {
            combine(
                walletManager.keyManager.filterNotNull(),
                appConfigurationManager.startupParams.filterNotNull(),
            ) { keyManager, startupParams ->
                NodeParams(
                    chain = chain,
                    loggerFactory = loggerFactory,
                    keyManager = keyManager,
                ).copy(
                    zeroConfPeers = setOf(trampolineNodeId),
                    liquidityPolicy = MutableStateFlow(startupParams.liquidityPolicy),
                )
            }.collect {
                log.info { "hello!" }
                log.info { "nodeid=${it.nodeId}" }
                log.info { "commit=${BuildVersions.PHOENIX_COMMIT}" }
                log.info { "lightning-kmp version=${BuildVersions.LIGHTNING_KMP_VERSION}" }
                _nodeParams.value = it
            }
        }
    }

    /** See [NodeParams.defaultOffer]. Returns an [OfferTypes.OfferAndKey] object. */
    suspend fun defaultOffer(): OfferTypes.OfferAndKey {
        return nodeParams.filterNotNull().first().defaultOffer(trampolineNodeId)
    }

    companion object {
        val chain = Chain.Regtest
        val trampolineNodeId = PublicKey.fromHex("02e7185d5c48e0004b6531a04d4d0182f6c9a6501c29f584729dc8c79b989c7292")
        val trampolineNodeUri = NodeUri(id = trampolineNodeId, "10.0.2.2", 29735)
        val trampolineNodeOnionUri = NodeUri(id = trampolineNodeId, "iq7zhmhck54vcax2vlrdcavq2m32wao7ekh6jyeglmnuuvv3js57r4id.onion", 9735)
        const val remoteSwapInXpub = "tpubDAyurcGut5Rwz28dYUujfeMk4fFF8FyLn22sQmrY9TbW8yq2y6ByA3dawNV869epZzRE6YfaGLW6D1WgTqnyB4iS7tX4RP3RUBLbB81JSCq"
        val defaultLiquidityPolicy = LiquidityPolicy.Auto(
            inboundLiquidityTarget = null, // auto inbound liquidity is disabled (it must be purchased manually)
            maxAbsoluteFee = 5_000.sat,
            maxRelativeFeeBasisPoints = 50_00 /* 50% */,
            skipAbsoluteFeeCheck = false,
            maxAllowedFeeCredit = 0.msat, // no fee credit
        )

        val payToOpenFeeBase = 100
    }
}