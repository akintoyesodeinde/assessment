package com.example.test.service;

import com.example.test.dto.CreateUserRequest;
import com.example.test.dto.CreateUserResponse;
import com.example.test.dto.DoTransDto;
import com.example.test.dto.FundWalletRequest;
import com.example.test.dto.RegisterRequest;
import com.example.test.dto.TransferResponse;
import com.example.test.dto.WalletEntryView;
import com.example.test.dto.WebTransferRequest;

import java.util.List;

public interface ServiceCall {

    CreateUserResponse createUserAndAccount(CreateUserRequest request);

    TransferResponse doIntraTransfer(DoTransDto request);

    Long registerUser(RegisterRequest request);

    CreateUserResponse createWalletForUser(String email);

    CreateUserResponse getWalletForUser(String email);

    TransferResponse doTransferFromUserWallet(String email, WebTransferRequest request);

    TransferResponse fundWalletForUser(String email, FundWalletRequest request);

    List<WalletEntryView> listEntriesForUser(String email);

}
