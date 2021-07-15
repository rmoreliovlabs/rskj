/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.util;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionArguments;
import org.ethereum.core.TransactionPool;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.vm.GasCost;

public class TransactionArgumentsUtil {

	private TransactionArgumentsUtil() {}

	private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);

	public static final String ERR_INVALID_CHAIN_ID = "Invalid chainId: ";

	/**
	 * transform the Web3.CallArguments in TransactionArguments that can be used in
	 * the TransactionBuilder
	 */
	public static TransactionArguments processArguments(Web3.CallArguments argsParam, TransactionPool transactionPool, Account senderAccount, byte defaultChainId) {

		TransactionArguments argsRet = new TransactionArguments();

		argsRet.setFrom(argsParam.from);

		argsRet.setTo(stringHexToByteArray(argsParam.to));

		argsRet.setNonce(stringNumberAsBigInt(argsParam.nonce, () -> transactionPool.getPendingState().getNonce(senderAccount.getAddress())));

		argsRet.setValue(stringNumberAsBigInt(argsParam.value, () -> BigInteger.ZERO));

		argsRet.setGasPrice(stringNumberAsBigInt(argsParam.gasPrice, () -> BigInteger.ZERO));

		argsRet.setGasLimit(stringNumberAsBigInt(argsParam.gas, () -> null));

		if (argsRet.getGasLimit() == null) {
			argsRet.setGasLimit(stringNumberAsBigInt(argsParam.getGasLimit(), () -> DEFAULT_GAS_LIMIT));
		}

		if (argsParam.data != null && argsParam.data.startsWith("0x")) {
			argsRet.setData(argsParam.data.substring(2));
			argsParam.data = argsRet.getData(); // needs to change the parameter because some places expect the changed value after sendTransaction call
		}

		argsRet.setChainId(hexToChainId(argsParam.chainId));
		if (argsRet.getChainId() == 0) {
			argsRet.setChainId(defaultChainId);
		}

		return argsRet;
	}

	private static BigInteger stringNumberAsBigInt(String number, Supplier<BigInteger> getDefaultValue) {

		BigInteger ret = Optional.ofNullable(number).map(TypeConverter::stringNumberAsBigInt).orElseGet(getDefaultValue);

		return ret;
	}

	private static byte[] stringHexToByteArray(String value) {

		byte[] ret = Optional.ofNullable(value).map(TypeConverter::stringHexToByteArray).orElse(null);

		return ret;
	}

	private static byte hexToChainId(String hex) {
		if (hex == null) {
			return 0;
		}
		try {
			byte[] bytes = TypeConverter.stringHexToByteArray(hex);
			if (bytes.length != 1) {
				throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + hex);
			}

			return bytes[0];
		} catch (Exception e) {
			throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_CHAIN_ID + hex, e);
		}
	}

}