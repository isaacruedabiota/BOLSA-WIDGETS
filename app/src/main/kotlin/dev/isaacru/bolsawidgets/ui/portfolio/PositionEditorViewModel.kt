package dev.isaacru.bolsawidgets.ui.portfolio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.isaacru.bolsawidgets.domain.model.Position
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.repository.PortfolioRepository
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.ui.navigation.PositionEditorRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Which field is wrong. Mapped to Spanish text by the screen. */
enum class EditorFieldError {
    SYMBOL_REQUIRED,
    SYMBOL_UNKNOWN,
    QUANTITY_INVALID,
    PRICE_INVALID,
}

data class PositionEditorUiState(
    val isNew: Boolean = true,
    val symbol: String = "",
    val name: String = "",
    val exchange: String = "",
    val quantity: String = "",
    val buyPrice: String = "",
    val currency: String = "",
    val purchaseDate: LocalDate = LocalDate.now(),
    val notes: String = "",
    val verifiedQuote: Quote? = null,
    val symbolError: EditorFieldError? = null,
    val quantityError: EditorFieldError? = null,
    val priceError: EditorFieldError? = null,
    val isVerifying: Boolean = false,
    val isSaving: Boolean = false,
)

@HiltViewModel
class PositionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val portfolioRepository: PortfolioRepository,
    private val quoteRepository: QuoteRepository,
    clock: Clock,
) : ViewModel() {

    private val positionId: Long = savedStateHandle.toRoute<PositionEditorRoute>().positionId

    /**
     * The symbol and currency as they were stored. Re-verifying an untouched symbol would
     * make editing a note impossible offline, so a lot that was already verified once is
     * trusted as long as its ticker does not change.
     */
    private var storedSymbol: String? = null
    private var storedCurrency: String? = null

    private val state = MutableStateFlow(
        PositionEditorUiState(
            isNew = positionId == NEW_POSITION_ID,
            purchaseDate = LocalDate.now(clock),
        ),
    )
    val uiState: StateFlow<PositionEditorUiState> = state.asStateFlow()

    private val doneChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits once the lot has been saved or deleted and the screen can close. */
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    init {
        if (positionId != NEW_POSITION_ID) {
            viewModelScope.launch {
                portfolioRepository.getPosition(positionId)?.let(::fillFrom)
            }
        }
    }

    fun onSymbolChange(value: String) = state.update {
        // Editing the ticker invalidates the previous verification.
        it.copy(symbol = value.uppercase(), verifiedQuote = null, symbolError = null)
    }

    fun onNameChange(value: String) = state.update { it.copy(name = value) }

    fun onExchangeChange(value: String) = state.update { it.copy(exchange = value) }

    fun onQuantityChange(value: String) = state.update { it.copy(quantity = value, quantityError = null) }

    fun onBuyPriceChange(value: String) = state.update { it.copy(buyPrice = value, priceError = null) }

    fun onPurchaseDateChange(value: LocalDate) = state.update { it.copy(purchaseDate = value) }

    fun onNotesChange(value: String) = state.update { it.copy(notes = value) }

    /** Fills the identity fields from a symbol the picker already verified. */
    fun applyQuote(quote: Quote) = state.update { current ->
        current.copy(
            symbol = quote.symbol,
            name = current.name.ifBlank { quote.shortName ?: quote.symbol },
            exchange = current.exchange.ifBlank { quote.exchange.orEmpty() },
            currency = quote.currency,
            verifiedQuote = quote,
            symbolError = null,
        )
    }

    fun save() {
        val current = state.value
        val quantity = current.quantity.toDecimalOrNull()
        val price = current.buyPrice.toDecimalOrNull()

        val errors = current.copy(
            symbolError = if (current.symbol.isBlank()) EditorFieldError.SYMBOL_REQUIRED else null,
            quantityError = if (quantity == null || quantity <= 0.0) EditorFieldError.QUANTITY_INVALID else null,
            priceError = if (price == null || price < 0.0) EditorFieldError.PRICE_INVALID else null,
        )
        state.value = errors
        if (errors.symbolError != null || errors.quantityError != null || errors.priceError != null) return

        viewModelScope.launch {
            state.update { it.copy(isSaving = true) }

            val unchanged = current.symbol == storedSymbol && storedCurrency != null
            // A lot only reaches Room once its symbol has actually priced, so nothing
            // unpriceable can enter the portfolio. An untouched symbol was already
            // verified when it was first saved, so it does not pay for that again.
            val quote = when {
                unchanged -> null
                else -> current.verifiedQuote ?: verify(current.symbol)
            }
            if (!unchanged && quote == null) {
                state.update { it.copy(isSaving = false, symbolError = EditorFieldError.SYMBOL_UNKNOWN) }
                return@launch
            }

            // The currency belongs to the listing, never to the user: storing a US share
            // as EUR would silently misvalue the whole portfolio.
            val currency = quote?.currency ?: requireNotNull(storedCurrency)

            portfolioRepository.upsert(
                Position(
                    id = if (current.isNew) 0L else positionId,
                    symbol = quote?.symbol ?: current.symbol,
                    name = current.name.ifBlank { quote?.shortName ?: current.symbol },
                    exchange = current.exchange.ifBlank { quote?.exchange.orEmpty() },
                    quantity = requireNotNull(quantity),
                    averageBuyPrice = requireNotNull(price),
                    currency = currency,
                    purchaseDate = current.purchaseDate,
                    notes = current.notes.trim(),
                ),
            )
            state.update { it.copy(isSaving = false) }
            doneChannel.send(Unit)
        }
    }

    fun delete() {
        if (positionId == NEW_POSITION_ID) return
        viewModelScope.launch {
            portfolioRepository.delete(positionId)
            doneChannel.send(Unit)
        }
    }

    private suspend fun verify(symbol: String): Quote? {
        state.update { it.copy(isVerifying = true) }
        val quote = runCatching { quoteRepository.resolveSymbol(symbol) }.getOrNull()
        state.update { it.copy(isVerifying = false, verifiedQuote = quote) }
        return quote
    }

    private fun fillFrom(position: Position) {
        storedSymbol = position.symbol
        storedCurrency = position.currency
        applyStored(position)
    }

    private fun applyStored(position: Position) = state.update {
        it.copy(
            isNew = false,
            symbol = position.symbol,
            name = position.name,
            exchange = position.exchange,
            quantity = Format.decimal(position.quantity),
            buyPrice = Format.decimal(position.averageBuyPrice),
            currency = position.currency,
            purchaseDate = position.purchaseDate,
            notes = position.notes,
        )
    }

    /** Accepts both the Spanish decimal comma and the plain dot. */
    private fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

    /** Editable text for a stored number, without locale grouping separators. */
    private object Format {
        fun decimal(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }

    companion object {
        const val NEW_POSITION_ID = 0L
    }
}
