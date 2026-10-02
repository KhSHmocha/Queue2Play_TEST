import java.util.ArrayList;

// Doubly Linked List that stores the checkout requests.
// VIP requests are placed ahead of Regular requests.
public class CheckoutQueue {
    RentalRequest head;   // first request (served first)
    RentalRequest tail;   // last request

    boolean isEmpty() {
        return head == null;
    }

    // Add a request using the VIP / Regular priority rule
    void addRequest(RentalRequest newRequest) {
        if (head == null) {
            head = newRequest;
            tail = newRequest;
        } else if (newRequest.vip) {
            // Skip all VIP requests, then insert before the first Regular request
            RentalRequest current = head;
            while (current != null && current.vip) {
                current = current.next;
            }

            if (current == null) {
                addAtTail(newRequest);          // no Regular request in the queue
            } else {
                insertBefore(current, newRequest);
            }
        } else {
            addAtTail(newRequest);              // Regular: always at the end
        }
    }

    void addAtTail(RentalRequest newRequest) {
        tail.next = newRequest;
        newRequest.previous = tail;
        tail = newRequest;
    }

    void insertBefore(RentalRequest target, RentalRequest newRequest) {
        newRequest.next = target;
        newRequest.previous = target.previous;

        if (target.previous == null) {
            head = newRequest;                  // inserted at the very front
        } else {
            target.previous.next = newRequest;
        }

        target.previous = newRequest;
    }

    // Remove any request from the queue by fixing the links around it
    void removeRequest(RentalRequest request) {
        if (request.previous == null) {
            head = request.next;                // removing the first node
        } else {
            request.previous.next = request.next;
        }

        if (request.next == null) {
            tail = request.previous;            // removing the last node
        } else {
            request.next.previous = request.previous;
        }

        request.previous = null;
        request.next = null;
    }

    // Remove every pending request for one game. Returns how many were removed.
    int removeRequestsForGame(Game game) {
        int removed = 0;
        RentalRequest current = head;

        while (current != null) {
            RentalRequest nextNode = current.next;   // save before removing
            if (current.game == game) {
                removeRequest(current);
                removed++;
            }
            current = nextNode;
        }

        return removed;
    }

    boolean hasRequest(User client, Game game) {
        RentalRequest current = head;
        while (current != null) {
            if (current.client == client && current.game == game) {
                return true;
            }
            current = current.next;
        }
        return false;
    }

    RentalRequest findByNumber(int queueNumber) {
        RentalRequest current = head;
        while (current != null) {
            if (current.queueNumber == queueNumber) {
                return current;
            }
            current = current.next;
        }
        return null;
    }

    ArrayList<RentalRequest> getRequestsOf(User client) {
        ArrayList<RentalRequest> list = new ArrayList<>();
        RentalRequest current = head;
        while (current != null) {
            if (current.client == client) {
                list.add(current);
            }
            current = current.next;
        }
        return list;
    }

    int getPosition(RentalRequest request) {
        int position = 1;
        RentalRequest current = head;
        while (current != null) {
            if (current == request) {
                return position;
            }
            current = current.next;
            position++;
        }
        return -1;
    }

    void printQueue() {
        if (head == null) {
            System.out.println("(The queue is empty)");
            return;
        }

        RentalRequest current = head;
        while (current != null) {
            System.out.printf("#%03d | %-3s | %-10s | %s%n",
                current.queueNumber,
                current.vip ? "VIP" : "REG",
                current.client.username,
                current.game.title);
            current = current.next;
        }
    }
}
