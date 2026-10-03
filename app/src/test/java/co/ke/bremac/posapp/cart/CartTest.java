package co.ke.bremac.posapp.cart;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class CartTest {
    @Test public void cannotExceedStockOrUseUnknownStock() {
        Cart.Line line = new Cart.Line(1, "Milk", "M1", 1, 100);
        assertFalse(line.canSetQuantity(1));
        line.setStock(true, true, 3);
        assertTrue(line.canSetQuantity(3));
        assertFalse(line.canSetQuantity(3.01));
        assertFalse(line.canSetQuantity(0));
        assertFalse(line.canSetQuantity(Double.NaN));
        assertFalse(line.canSetQuantity(Double.POSITIVE_INFINITY));
    }

    @Test public void fractionsAndUntrackedProductsAreSupported() {
        Cart.Line line = new Cart.Line(1, "Rice", "R1", 0.25, 100);
        line.setStock(true, true, 0.75);
        assertTrue(line.canSetQuantity(0.75));
        assertFalse(line.canSetQuantity(1));
        line.setStock(false, false, 0);
        assertTrue(line.canSetQuantity(1000));
    }

    @Test public void checkoutWarnsWhenStockDropsBelowCartQuantity() {
        Cart cart = new Cart();
        cart.add(1, "Milk", "M1", 100);
        Cart.Line line = cart.lines.get(0);
        line.setStock(true, true, 1);
        assertEquals("", cart.stockWarning());
        line.setStock(true, true, 0);
        assertTrue(cart.stockWarning().contains("Milk"));
        assertFalse(line.canSetQuantity(line.quantity));
    }

    @Test public void totalsFixedDiscountAndChange() {
        Cart cart = new Cart();
        cart.add(1, "Milk", "M1", 100);
        cart.add(1, "Milk", "M1", 100);
        cart.discountType = "fixed";
        cart.discountAmount = 30;
        Cart.Payment p = new Cart.Payment();
        p.amount = 200;
        cart.payments.add(p);
        assertEquals(200, cart.subtotal(2), 0.001);
        assertEquals(30, cart.discount(2), 0.001);
        assertEquals(170, cart.total(2), 0.001);
        assertEquals(30, cart.change(2), 0.001);
        assertEquals(0, cart.balance(2), 0.001);
    }

    @Test public void totalsPercentageDiscountAndRounding() {
        Cart cart = new Cart();
        cart.add(1, "Tea", "T1", 99.995);
        cart.discountType = "percentage";
        cart.discountAmount = 10;
        assertEquals(100.00, cart.subtotal(2), 0.001);
        assertEquals(10.00, cart.discount(2), 0.001);
        assertEquals(90.00, cart.total(2), 0.001);
    }
}
