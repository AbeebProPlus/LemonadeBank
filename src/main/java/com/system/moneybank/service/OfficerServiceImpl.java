package com.system.moneybank.service;

import com.system.moneybank.dtos.request.CreateAccountRequest;
import com.system.moneybank.dtos.request.CreditDebitRequest;
import com.system.moneybank.dtos.request.EnquiryRequest;
import com.system.moneybank.dtos.request.TransactionHistoryRequest;
import com.system.moneybank.dtos.response.Response;
import com.system.moneybank.dtos.response.TransactionHistoryResponse;
import com.system.moneybank.exceptions.CustomerNotFound;
import com.system.moneybank.models.*;
import com.system.moneybank.repository.BankingHallTransactionRepo;
import com.system.moneybank.repository.OfficerRepo;
import com.system.moneybank.service.emailService.EmailDetails;
import com.system.moneybank.service.emailService.EmailSenderService;
import com.system.moneybank.utils.AccountUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.system.moneybank.models.AccountStatus.ACTIVE;
import static com.system.moneybank.models.TransactionStatus.SUCCESS;
import static com.system.moneybank.models.TransactionType.CREDIT;
import static com.system.moneybank.models.TransactionType.DEBIT;
import static com.system.moneybank.utils.AccountUtils.*;
import static com.system.moneybank.utils.AccountUtils.ACCOUNT_CREATION_MESSAGE;
import static java.math.BigDecimal.ZERO;

@Service
@RequiredArgsConstructor
public class OfficerServiceImpl implements OfficerService{
    private final UserService userService;
    private final EmailSenderService emailSenderService;
    private final TransactionService transactionService;
    private final OfficerRepo officerRepo;

    @Override
    public Response createBankAccount(CreateAccountRequest request) {
        if (accountExists(request)) return Response.builder().code(ACCOUNT_EXISTS_CODE).message(ACCOUNT_EXISTS_MESSAGE).build();
        Customer user = createUser(request);
        Customer savedUser = userService.save(user);
        String message = "Dear " + savedUser.getFirstName() + " you now have an account with Lemonade Bank." +
                "\nAccount name: " + savedUser.getFirstName() + " " + savedUser.getLastName() + " " + savedUser.getMiddleName() +
                "\nAccount number: " + savedUser.getAccountNumber() + "\nDate: " + LocalDate.now() +
                "\nTime: " + LocalTime.now() + "\nThank you for banking with us";
        EmailDetails details = mailMessage(savedUser, "Account creation notification", savedUser.getEmail(), message);
        emailSenderService.sendMail(details);
        return transactionResponse(savedUser, ACCOUNT_CREATION_CODE, ACCOUNT_CREATION_MESSAGE);
    }

    @Override
    public Response checkAccountBalance(EnquiryRequest enquiryRequest) {
        if (!isValidAccountNumber(enquiryRequest)) return Response.builder().code(ACCOUNT_NOT_FOUND_CODE).message(ACCOUNT_NOT_FOUND_MESSAGE).build();
        Customer user = userService.findByAccountNumber(enquiryRequest.getAccountNumber());
        return Response.builder().code(ACCOUNT_FOUND_CODE).message(ACCOUNT_FOUND_MESSAGE).accountDetails(getAccountDetails(user)).build();
    }

    @Override
    public Response creditAccount(CreditDebitRequest request) {
        boolean accountExists = userService.existsByAccountNumber(request.getAccountNumber());
        if (!accountExists) return Response.builder().code(ACCOUNT_NOT_FOUND_CODE).message(ACCOUNT_NOT_FOUND_MESSAGE).build();
        if (isInValidAmount(request)) return Response.builder().code(ACCOUNT_CREDIT_DECLINED_CODE).message(ACCOUNT_CREDIT_DECLINED_MESSAGE).build();
        Customer creditedUser = creditUser(request);
        Officer officer = officerRepo.findById(request.getOfficerId()).get();
        BankingHallTransaction bankingHallTransaction = createTransaction(request, creditedUser, CREDIT, SUCCESS, officer);
        transactionService.saveTransaction(bankingHallTransaction);
        String message = "Dear " + creditedUser.getFirstName() + "A credit transaction occurred on your account " +
                "\nAmount: " + request.getAmount() + "\nCurrent balance: "  + creditedUser.getAccountBalance() +
                "\nFrom: " + request.getDepositorName() + "\nDate: " + LocalDate.now() + "\nTime: " + LocalTime.now();
        EmailDetails details = mailMessage(creditedUser, "Credit transaction notification", creditedUser.getEmail(), message);
        emailSenderService.sendMail(details);
        return transactionResponse(creditedUser, ACCOUNT_CREDITED_CODE, ACCOUNT_CREDITED_MESSAGE);
    }

    private BankingHallTransaction createTransaction(CreditDebitRequest request, Customer creditedUser,
                                                     TransactionType type, TransactionStatus status, Officer officer) {
        return BankingHallTransaction.builder()
                .amount(request.getAmount()).accountNumber(creditedUser.getAccountNumber())
                .processedBy(officer.getUserName()).type(type).status(status).date(LocalDate.now())
                .time(LocalTime.now()).officer(officer).build();
    }


    @Override
    public Response debitAccount(CreditDebitRequest request) {
        boolean accountExists = userService.existsByAccountNumber(request.getAccountNumber());
        if (!accountExists) return Response.builder().code(ACCOUNT_NOT_FOUND_CODE).message(ACCOUNT_NOT_FOUND_MESSAGE).build();
        Customer user = userService.findByAccountNumber(request.getAccountNumber());
        BigDecimal amountInUserAccount = user.getAccountBalance();
        if (isInValidAmount(request)) return Response.builder().code(ACCOUNT_DEBIT_DECLINED_CODE).message(ACCOUNT_DEBIT_DECLINED_MESSAGE).build();
        if (request.getAmount().compareTo(amountInUserAccount) > 0) return Response.builder().code(ACCOUNT_DEBIT_DECLINED_CODE).message(ACCOUNT_DEBIT_DECLINED_MESSAGE).build();
        Customer debitedUser = debitUser(request);
        Officer officer = officerRepo.findById(request.getOfficerId()).get();
        BankingHallTransaction bankingHallTransaction = createTransaction(request, debitedUser, DEBIT, SUCCESS, officer);
        transactionService.saveTransaction(bankingHallTransaction);
        String message = "Dear " + debitedUser.getFirstName() + " A debit transaction occurred on your account." +
                "\nAmount: " + request.getAmount() + "\nCurrent balance: "  + debitedUser.getAccountBalance() + "\nDate: " + LocalDateTime.now() + "\nTime: " + LocalTime.now();
        EmailDetails details = mailMessage(debitedUser, "Debit transaction notification", debitedUser.getEmail(), message);
        emailSenderService.sendMail(details);
        return transactionResponse(debitedUser, ACCOUNT_DEBIT_CODE, ACCOUNT_DEBIT_MESSAGE);
    }

    @Override
    public String checkAccountName(EnquiryRequest enquiryRequest) {
        boolean accountExists = userService.existsByAccountNumber(enquiryRequest.getAccountNumber());
        if (!accountExists) return ACCOUNT_NOT_FOUND_MESSAGE;
        Customer user = userService.findByAccountNumber(enquiryRequest.getAccountNumber());
        return user.getFirstName() + " " + user.getLastName() + " " + user.getMiddleName();
    }

    @Override
    public TransactionHistoryResponse getAllTransactionsDoneByCustomer(TransactionHistoryRequest request) {
        try {
            Customer customer = userService.findByAccountNumber(request.getAccountNumber());
            if (customer == null) throw new CustomerNotFound(ACCOUNT_NOT_FOUND_MESSAGE);
            return TransactionHistoryResponse.builder().code(ACCOUNT_FOUND_CODE).message(ACCOUNT_FOUND_MESSAGE).transactionList(customer.getTransactionList()).build();
        } catch (Exception ex) {
            return TransactionHistoryResponse.builder().code(ACCOUNT_NOT_FOUND_CODE).message(ex.getMessage()).transactionList(null).build();
        }
    }

    @Override
    public List<BankingHallTransaction> viewAllBankingHallTransactions() {
        return transactionService.viewAllBankingHallTransactions();
    }


    private Customer creditUser(CreditDebitRequest request) {
        Customer userToBeCredited = userService.findByAccountNumber(request.getAccountNumber());
        BigDecimal userToBeCreditedBalance = userToBeCredited.getAccountBalance();
        userToBeCredited.setAccountBalance(userToBeCreditedBalance.add(request.getAmount()));
        return userService.save(userToBeCredited);
    }
    private Customer debitUser(CreditDebitRequest request){
        Customer userToBeDebited = userService.findByAccountNumber(request.getAccountNumber());
        BigDecimal userToBeDebitedBalance = userToBeDebited.getAccountBalance();
        userToBeDebited.setAccountBalance(userToBeDebitedBalance.subtract(request.getAmount()));
        return userService.save(userToBeDebited);
    }
    private boolean isValidAccountNumber(EnquiryRequest enquiryRequest) {
        return userService.existsByAccountNumber(enquiryRequest.getAccountNumber());
    }
    private boolean isInValidAmount(CreditDebitRequest request) {
        return request.getAmount().compareTo(ZERO) <= 0;
    }

    private boolean accountExists(CreateAccountRequest request) {
        return userService.existsByEmail(request.getEmail());
    }
    private Customer createUser(CreateAccountRequest request) {
        return Customer.builder().firstName(request.getFirstName()).lastName(request.getLastName()).middleName(request.getMiddleName())
                .email(request.getEmail()).phoneNumber(request.getPhoneNumber()).secondPhoneNumber(request.getSecondPhoneNumber())
                .address(request.getAddress()).gender(request.getGender())
                .accountNumber(AccountUtils.generateAccountNumber()).accountBalance(ZERO).accountStatus(ACTIVE)
                .build();
    }
    private EmailDetails mailMessage(Customer savedUser, String subject, String email, String message) {
        return EmailDetails.builder()
                .subject(subject)
                .recipientMailAddress(email)
                .message(message)
                .build();
    }
    private Response transactionResponse(Customer savedUser, String code, String message) {
        return Response.builder().code(code).message(message).accountDetails(getAccountDetails(savedUser)).build();
    }
    private AccountDetails getAccountDetails(Customer savedUser) {
        return AccountDetails.builder().accountName(savedUser.getFirstName() + " " +
                        savedUser.getLastName() + " " +
                        savedUser.getMiddleName())
                .accountBalance(savedUser.getAccountBalance())
                .accountNumber(savedUser.getAccountNumber())
                .build();
    }

    @PostConstruct
    private void createOfficer() {
        if (!officerExists("AdebayoCustomerCare1")) {
            Officer officer = Officer.builder()
                    .userName("AdebayoCustomerCare1")
                    .role(Role.OFFICER)
                    .build();
            officerRepo.save(officer);
        }
    }

    private boolean officerExists(String userName) {
        Optional<Officer> officer = officerRepo.findByUserName(userName);
        return officer.isPresent();
    }
}